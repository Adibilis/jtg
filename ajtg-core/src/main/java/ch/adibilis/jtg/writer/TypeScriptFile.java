package ch.adibilis.jtg.writer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TypeScriptFile {

    private String relativePath;
    private final List<Import> imports = new ArrayList<>();
    private String body;

    public record Import(String path, String defaultImport, Set<String> namedImports) {
        public Import(String path, Set<String> namedImports) {
            this(path, null, namedImports);
        }

        public Import(String path, String defaultImport) {
            this(path, defaultImport, Set.of());
        }
    }

    public TypeScriptFile() {}

    public TypeScriptFile(String relativePath) {
        this.relativePath = relativePath;
    }

    public String resolveImportPath(TypeScriptFile other) {
        Path thisDir = Path.of(relativePath).getParent();
        Path otherPath = Path.of(other.relativePath.replaceAll("\\.ts$", ""));
        String relative = thisDir.relativize(otherPath).toString();
        if (!relative.startsWith(".")) {
            relative = "./" + relative;
        }
        return relative;
    }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }
    public List<Import> getImports() { return imports; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}
