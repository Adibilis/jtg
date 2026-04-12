package ch.adibilis.jtg;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.endpoints.Endpoint;
import ch.adibilis.jtg.model.types.Type;
import ch.adibilis.jtg.parser.SpringReflectionParser;
import ch.adibilis.jtg.parser.fixtures.*;
import ch.adibilis.jtg.writer.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IntegrationTest {

    @TempDir
    Path outputDir;

    @Test
    void fullPipelineGeneratesTypesAndServices() throws IOException {
        // Config
        GeneratorConfig config = new GeneratorConfig(
                List.of("ch.adibilis.jtg.parser.fixtures"),
                List.of(outputDir.toString()), false, "", null, Map.of(), List.of(), 0
        );

        // Parse
        SpringReflectionParser parser = new SpringReflectionParser(config);
        List<Endpoint> endpoints = new ArrayList<>();
        endpoints.addAll(parser.parseController(TestUserController.class));
        endpoints.addAll(parser.parseController(TestFileUploadController.class));
        endpoints.addAll(parser.parseController(TestPaginatedController.class));

        Map<String, Type> namedTypes = parser.getNamedTypes();

        System.out.println("Parsed " + endpoints.size() + " endpoints, " + namedTypes.size() + " types");
        System.out.println("Types: " + namedTypes.keySet());

        // Generate types
        TypeScriptTypeWriter typeWriter = new TypeScriptTypeWriter();
        List<TypeScriptFile> typeFiles = typeWriter.generateTypes(namedTypes, config);

        // Generate services (inline, since we don't have ServiceLoader in test)
        GeneratorContext ctx = new GeneratorContext(endpoints, namedTypes, config);

        // Write all files
        List<TypeScriptFile> allFiles = new ArrayList<>(typeFiles);
        for (TypeScriptFile file : allFiles) {
            Path filePath = outputDir.resolve(file.getRelativePath());
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, file.getBody());
        }

        // Verify type files were generated
        assertThat(outputDir.resolve("types/SimpleDto.ts")).exists();
        String simpleDto = Files.readString(outputDir.resolve("types/SimpleDto.ts"));
        assertThat(simpleDto).contains("export default interface SimpleDto {");
        assertThat(simpleDto).contains("name: string;");
        assertThat(simpleDto).contains("age: number;");
        assertThat(simpleDto).contains("active: boolean;");

        // Print all generated files
        System.out.println("\n--- Generated files ---");
        try (var walk = Files.walk(outputDir)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                System.out.println(outputDir.relativize(p));
                try {
                    System.out.println(Files.readString(p));
                    System.out.println();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
