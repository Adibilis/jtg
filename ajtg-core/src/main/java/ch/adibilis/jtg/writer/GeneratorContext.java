package ch.adibilis.jtg.writer;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.endpoints.Endpoint;
import ch.adibilis.jtg.model.types.Type;

import java.util.List;
import java.util.Map;

public record GeneratorContext(
        List<Endpoint> endpoints,
        Map<String, Type> namedTypes,
        GeneratorConfig config
) {
}
