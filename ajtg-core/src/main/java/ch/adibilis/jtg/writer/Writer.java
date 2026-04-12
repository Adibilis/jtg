package ch.adibilis.jtg.writer;

import java.util.List;

public interface Writer {
    List<TypeScriptFile> generate(GeneratorContext context);
    default boolean handlesTypes() { return false; }
}
