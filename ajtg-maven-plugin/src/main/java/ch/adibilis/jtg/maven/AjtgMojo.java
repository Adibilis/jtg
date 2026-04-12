package ch.adibilis.jtg.maven;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.endpoints.Endpoint;
import ch.adibilis.jtg.model.types.Type;
import ch.adibilis.jtg.parser.SpringReflectionParser;
import ch.adibilis.jtg.writer.GeneratorContext;
import ch.adibilis.jtg.writer.TypeScriptFile;
import ch.adibilis.jtg.writer.TypeScriptTypeWriter;
import ch.adibilis.jtg.writer.Writer;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

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

    @Parameter(required = true)
    private List<String> basePackages;

    @Parameter(required = true)
    private List<String> outputDirectories;

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

            // Collect generated files
            List<TypeScriptFile> allFiles = new ArrayList<>();

            // Check if any writer handles types (e.g., ZodTypeWriter)
            boolean typesHandled = writers.stream().anyMatch(Writer::handlesTypes);

            if (!typesHandled) {
                // Use TypeScriptTypeWriter for type generation
                TypeScriptTypeWriter typeWriter = new TypeScriptTypeWriter();
                allFiles.addAll(typeWriter.generateTypes(namedTypes, config));
            }

            // Run all ServiceLoader writers
            for (Writer writer : writers) {
                allFiles.addAll(writer.generate(context));
            }

            // Deduplicate by relativePath (last one wins)
            Map<String, TypeScriptFile> deduped = new LinkedHashMap<>();
            for (TypeScriptFile file : allFiles) {
                deduped.put(file.getRelativePath(), file);
            }

            // Write files to all output directories
            for (String outputDir : outputDirectories) {
                Path outPath = Path.of(outputDir);
                for (TypeScriptFile file : deduped.values()) {
                    Path filePath = outPath.resolve(file.getRelativePath());
                    Files.createDirectories(filePath.getParent());

                    StringBuilder content = new StringBuilder();
                    // Import statements are rendered by the writer into the body
                    content.append(file.getBody());

                    Files.writeString(filePath, content.toString());
                }
            }

            getLog().info("Generated " + deduped.size() + " files to " + outputDirectories.size() + " output directories");

        } catch (Exception e) {
            throw new MojoExecutionException("AJTG generation failed", e);
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

            // Sub-modules
            if (subModules != null) {
                for (String subModule : subModules) {
                    Path subModulePath = Path.of(project.getBasedir().getParent(), subModule,
                            "target", "classes");
                    if (Files.exists(subModulePath)) {
                        urls.add(subModulePath.toUri().toURL());
                    }
                }
            }

            return new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to build classloader", e);
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
        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
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
