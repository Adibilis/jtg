package ch.adibilis.jtg.writer;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.types.*;

import java.util.stream.Collectors;

public final class TypeScriptTypeMapper {

    private TypeScriptTypeMapper() {}

    public static String map(Type type, GeneratorConfig config) {
        return switch (type) {
            case PrimitiveType p -> mapPrimitive(p, config);
            case ArrayType a -> map(a.subType(), config) + "[]";
            case MapType m -> "Record<" + map(m.keyType(), config) + ", " + map(m.valueType(), config) + ">";
            case OptionalType o -> map(o.subType(), config) + " | null";
            case EnumType e -> e.name();
            case UnionType u -> u.variants().stream().map(ObjectType::getName).collect(Collectors.joining(" | "));
            case ObjectType o -> mapObject(o, config);
            case LiteralType l -> "'" + l.value() + "'";
            case TypeVar t -> t.name();
        };
    }

    private static String mapPrimitive(PrimitiveType p, GeneratorConfig config) {
        return switch (p) {
            case Int, Double -> "number";
            case BigInt -> "bigint";
            case String -> "string";
            case Boolean -> "boolean";
            case Void -> "void";
            case Date -> config.dateAsString() ? "string" : "Date";
            case File -> "File";
        };
    }

    private static String mapObject(ObjectType o, GeneratorConfig config) {
        if (o.getGenericArgInstantiations().isEmpty()) {
            return o.getName();
        }
        String args = o.getGenericArgInstantiations().stream()
                .map(t -> map(t, config))
                .collect(Collectors.joining(", "));
        return o.getName() + "<" + args + ">";
    }
}
