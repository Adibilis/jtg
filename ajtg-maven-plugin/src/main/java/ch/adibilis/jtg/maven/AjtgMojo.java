package ch.adibilis.jtg.maven;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.endpoints.Endpoint;
import ch.adibilis.jtg.model.types.Type;
import ch.adibilis.jtg.parser.SpringReflectionParser;
import ch.adibilis.jtg.writer.GeneratorContext;
import ch.adibilis.jtg.writer.TypeScriptFile;
import ch.adibilis.jtg.writer.TypeScriptTypeWriter;
import ch.adibilis.jtg.writer.Writer;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class AjtgMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private org.apache.maven.execution.MavenSession session;

    @Component
    private ProjectBuilder projectBuilder;

    @Parameter(required = true)
    private List<String> basePackages;

    @Parameter(required = true)
    private List<String> outputDirectories;

    /**
     * Output directories that receive only files emitted by type-handling writers
     * (those whose {@link Writer#handlesTypes()} returns true) — typically the
     * generated TypeScript interfaces / enums / Zod schemas. Endpoint-writer
     * output (Angular services etc.) is filtered out before writing.
     * <p>
     * Useful when a consumer wants the shared type definitions but has no use for
     * framework-specific service files — e.g. a Next.js site that imports types
     * from a Spring Boot project that also publishes Angular services.
     */
    @Parameter
    private List<String> typesOnlyOutputDirectories;

    @Parameter(defaultValue = "false")
    private boolean dateAsString;

    @Parameter(defaultValue = "")
    private String headerSuffix;

    @Parameter(defaultValue = "../../../environments/environment")
    private String environmentImportPath;

    @Parameter
    private Map<String, String> customTypeMappings;

    @Parameter
    private List<String> explicitlyMapClasses;

    @Parameter
    private List<String> subModules;

    @Parameter(defaultValue = "0")
    private int paginationOffset;

    @Parameter(defaultValue = "true")
    private boolean clean;

    @Override
    public void execute() throws MojoExecutionException {
        try {
            GeneratorConfig config = new GeneratorConfig(
                    basePackages, outputDirectories, dateAsString, headerSuffix,
                    environmentImportPath,
                    customTypeMappings != null ? customTypeMappings : Map.of(),
                    explicitlyMapClasses != null ? explicitlyMapClasses : List.of(),
                    paginationOffset
            );

            // Build classpath URLClassLoader
            URLClassLoader classLoader = buildClassLoader();

            // Parse controllers
            SpringReflectionParser parser = new SpringReflectionParser(config);
            List<Endpoint> allEndpoints = new ArrayList<>();

            for (String basePackage : basePackages) {
                List<Class<?>> controllers = scanControllers(classLoader, basePackage);
                for (Class<?> controller : controllers) {
                    allEndpoints.addAll(parser.parseController(controller));
                }
            }

            // Parse explicitly mapped classes
            for (String className : config.explicitlyMapClasses()) {
                Class<?> clazz = classLoader.loadClass(className);
                parser.resolveType(clazz);
            }

            Map<String, Type> namedTypes = parser.getNamedTypes();

            getLog().info("Parsed " + allEndpoints.size() + " endpoints and " + namedTypes.size() + " types");

            // Create context
            GeneratorContext context = new GeneratorContext(allEndpoints, namedTypes, config);

            // Discover writers via ServiceLoader
            ServiceLoader<Writer> writerLoader = ServiceLoader.load(Writer.class, classLoader);
            List<Writer> writers = new ArrayList<>();
            writerLoader.forEach(writers::add);

            // Phase 1: type writers run first so their output is available to
            // endpoint writers (which need to know paths + default-vs-named import
            // shape for every referenced type).
            List<TypeScriptFile> typeFilesEmitted = new ArrayList<>();
            boolean typesHandled = writers.stream().anyMatch(Writer::handlesTypes);

            if (!typesHandled) {
                // Use TypeScriptTypeWriter for type generation
                TypeScriptTypeWriter typeWriter = new TypeScriptTypeWriter();
                typeFilesEmitted.addAll(typeWriter.generateTypes(namedTypes, config));
            } else {
                for (Writer w : writers) {
                    if (w.handlesTypes()) {
                        typeFilesEmitted.addAll(w.generate(context));
                    }
                }
            }

            // Build a simple-name → file map of every type file emitted so far,
            // so endpoint writers can resolve the relative path + import shape.
            Map<String, TypeScriptFile> typeFiles = new LinkedHashMap<>();
            for (TypeScriptFile f : typeFilesEmitted) {
                String name = f.getRelativePath();
                name = name.substring(name.lastIndexOf('/') + 1).replace(".ts", "");
                typeFiles.put(name, f);
            }

            GeneratorContext endpointContext = new GeneratorContext(
                    context.endpoints(), context.namedTypes(), context.config(), typeFiles);

            // Phase 2: non-type writers (endpoint writers) run with the enriched context.
            List<TypeScriptFile> endpointFilesEmitted = new ArrayList<>();
            for (Writer writer : writers) {
                if (writer.handlesTypes()) continue;
                endpointFilesEmitted.addAll(writer.generate(endpointContext));
            }

            // Deduplicate by relativePath across both phases (last one wins) and
            // keep separate sets for the types-only output filter below.
            Map<String, TypeScriptFile> deduped = new LinkedHashMap<>();
            for (TypeScriptFile file : typeFilesEmitted) deduped.put(file.getRelativePath(), file);
            for (TypeScriptFile file : endpointFilesEmitted) deduped.put(file.getRelativePath(), file);

            Set<String> typeFilePaths = new HashSet<>();
            for (TypeScriptFile file : typeFilesEmitted) typeFilePaths.add(file.getRelativePath());

            List<String> typesOnlyDirs = typesOnlyOutputDirectories != null
                    ? typesOnlyOutputDirectories : List.of();

            // Clean every output directory (full + types-only) before writing.
            if (clean) {
                List<String> allDirs = new ArrayList<>(outputDirectories.size() + typesOnlyDirs.size());
                allDirs.addAll(outputDirectories);
                allDirs.addAll(typesOnlyDirs);
                for (String outputDir : allDirs) {
                    Path outPath = Path.of(outputDir);
                    if (Files.exists(outPath)) {
                        try (var walk = Files.walk(outPath)) {
                            walk.sorted(Comparator.reverseOrder())
                                    .forEach(p -> {
                                        try {
                                            if (!p.equals(outPath)) {
                                                Files.delete(p);
                                            }
                                        } catch (Exception e) {
                                            getLog().warn("Failed to delete " + p + ": " + e.getMessage());
                                        }
                                    });
                        }
                        getLog().info("Cleaned output directory: " + outPath);
                    }
                }
            }

            // Write all files to full output directories.
            for (String outputDir : outputDirectories) {
                writeFiles(outputDir, deduped.values());
            }

            // Write only type files to types-only output directories.
            for (String outputDir : typesOnlyDirs) {
                List<TypeScriptFile> filtered = new ArrayList<>();
                for (TypeScriptFile f : deduped.values()) {
                    if (typeFilePaths.contains(f.getRelativePath())) filtered.add(f);
                }
                writeFiles(outputDir, filtered);
            }

            getLog().info("Generated " + deduped.size() + " files to " + outputDirectories.size()
                    + " full output directories and " + typesOnlyDirs.size() + " types-only output directories");

        } catch (Exception e) {
            throw new MojoExecutionException("AJTG generation failed", e);
        }
    }

    private void writeFiles(String outputDir, Iterable<TypeScriptFile> files) throws java.io.IOException {
        Path outPath = Path.of(outputDir);
        for (TypeScriptFile file : files) {
            Path filePath = outPath.resolve(file.getRelativePath());
            Files.createDirectories(filePath.getParent());
            // Imports were rendered into the body by each writer's own pass.
            Files.writeString(filePath, file.getBody());
        }
    }

    private URLClassLoader buildClassLoader() throws MojoExecutionException {
        try {
            List<URL> urls = new ArrayList<>();

            // Project's compiled classes
            urls.add(new File(project.getBuild().getOutputDirectory()).toURI().toURL());

            // Project's compile classpath
            for (String element : project.getCompileClasspathElements()) {
                urls.add(new File(element).toURI().toURL());
            }

            // Sub-modules: resolve their full dependency classpath
            if (subModules != null) {
                for (String subModule : subModules) {
                    // Add compiled classes
                    Path subModulePath = resolveSubModuleBase(subModule)
                            .resolve("target").resolve("classes");
                    if (Files.exists(subModulePath)) {
                        urls.add(subModulePath.toUri().toURL());
                    }
                    // Resolve and add all dependency JARs
                    MavenProject subProject = findAndResolveSubModule(subModule);
                    if (subProject != null) {
                        for (Artifact artifact : subProject.getArtifacts()) {
                            if (artifact.getFile() != null) {
                                urls.add(artifact.getFile().toURI().toURL());
                            }
                        }
                    }
                }
            }

            return new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build classloader", e);
        }
    }

    /**
     * Resolves a subModule name to its root directory.
     * SubModules are named relative to the parent (root) project, so when the plugin
     * runs inside a child module, we walk up to the parent's basedir first.
     */
    private Path resolveSubModuleBase(String subModule) {
        MavenProject parent = project.getParent();
        Path base = (parent != null && parent.getBasedir() != null)
                ? parent.getBasedir().toPath()
                : project.getBasedir().toPath();
        return base.resolve(subModule);
    }

    private MavenProject findAndResolveSubModule(String subModule) throws MojoExecutionException {
        Path subModulePom = resolveSubModuleBase(subModule).resolve("pom.xml");
        if (!Files.exists(subModulePom)) {
            getLog().warn("Submodule pom not found: " + subModulePom);
            return null;
        }
        try {
            ProjectBuildingRequest request = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
            request.setResolveDependencies(true);
            ProjectBuildingResult result = projectBuilder.build(subModulePom.toFile(), request);
            MavenProject subProject = result.getProject();
            getLog().info("Resolved submodule " + subModule + " with " +
                    subProject.getArtifacts().size() + " dependencies");
            return subProject;
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to resolve submodule " + subModule, e);
        }
    }

    private List<Class<?>> scanControllers(URLClassLoader classLoader, String basePackage) {
        List<Class<?>> controllers = new ArrayList<>();
        String packagePath = basePackage.replace('.', '/');

        try {
            Enumeration<URL> resources = classLoader.getResources(packagePath);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                if (resource.getProtocol().equals("file")) {
                    File dir = new File(resource.toURI());
                    scanDirectory(dir, basePackage, classLoader, controllers);
                }
            }
        } catch (Exception e) {
            getLog().warn("Failed to scan package " + basePackage + ": " + e.getMessage());
        }

        return controllers;
    }

    private void scanDirectory(File dir, String packageName, URLClassLoader classLoader,
                                List<Class<?>> controllers) {
        if (!dir.exists()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                scanDirectory(file, packageName + "." + file.getName(), classLoader, controllers);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().replace(".class", "");
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    if (clazz.isAnnotationPresent(
                            org.springframework.web.bind.annotation.RestController.class)) {

                        // Check -parameters flag on first method with params
                        checkParametersFlag(clazz);

                        controllers.add(clazz);
                    }
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    getLog().debug("Skipping class " + className + ": " + e.getMessage());
                }
            }
        }
    }

    private void checkParametersFlag(Class<?> clazz) {
        java.lang.reflect.Method[] declared = clazz.getDeclaredMethods();
        java.lang.reflect.Method[] candidates = Arrays.stream(declared)
                .filter(m -> !m.isSynthetic() && !m.isBridge())
                .sorted(Comparator.comparing(java.lang.reflect.Method::getName)
                        .thenComparingInt(java.lang.reflect.Method::getParameterCount))
                .toArray(java.lang.reflect.Method[]::new);
        for (java.lang.reflect.Method method : candidates) {
            if (method.getParameters().length > 0) {
                if (method.getParameters()[0].getName().equals("arg0")) {
                    throw new IllegalStateException(
                            "Parameter names not available for " + clazz.getName() +
                                    ". Compile with 'javac -parameters' flag. " +
                                    "Add <parameters>true</parameters> to maven-compiler-plugin configuration.");
                }
                return; // Only check the first method with params
            }
        }
    }
}
