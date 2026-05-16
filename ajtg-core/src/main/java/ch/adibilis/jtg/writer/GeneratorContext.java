package ch.adibilis.jtg.writer;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.endpoints.Endpoint;
import ch.adibilis.jtg.model.types.Type;

import java.util.List;
import java.util.Map;

public record GeneratorContext(
        List<Endpoint> endpoints,
        Map<String, Type> namedTypes,
        GeneratorConfig config,
        /**
         * Files emitted by the type-handling writers (those with {@link Writer#handlesTypes()})
         * keyed by simple type name. Populated by the Mojo before non-type writers run.
         * Empty when called from a type writer or when no type writers ran first.
         * <p>
         * Non-type writers (e.g. {@code AngularServiceWriter}) consult this to compute the
         * correct relative path to each referenced type's file and decide whether to use
         * default ({@link TypeScriptFile#hasDefaultExport()}) or named import.
         */
        Map<String, TypeScriptFile> typeFiles
) {
    public GeneratorContext(List<Endpoint> endpoints, Map<String, Type> namedTypes, GeneratorConfig config) {
        this(endpoints, namedTypes, config, Map.of());
    }
}
