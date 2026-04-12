package ch.adibilis.jtg.config;

import java.util.List;
import java.util.Map;

public record GeneratorConfig(
        List<String> basePackages,
        List<String> outputDirectories,
        boolean dateAsString,
        String headerSuffix,
        String environmentImportPath,
        Map<String, String> customTypeMappings,
        List<String> explicitlyMapClasses,
        int paginationOffset
) {
    public GeneratorConfig {
        if (basePackages == null || basePackages.isEmpty()) {
            throw new IllegalArgumentException("At least one basePackage is required");
        }
        if (outputDirectories == null || outputDirectories.isEmpty()) {
            throw new IllegalArgumentException("At least one outputDirectory is required");
        }
        if (customTypeMappings == null) customTypeMappings = Map.of();
        if (explicitlyMapClasses == null) explicitlyMapClasses = List.of();
        if (environmentImportPath == null) environmentImportPath = "../../../environments/environment";
        if (headerSuffix == null) headerSuffix = "";
    }
}
