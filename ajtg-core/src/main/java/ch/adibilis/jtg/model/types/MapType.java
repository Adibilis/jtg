package ch.adibilis.jtg.model.types;

public record MapType(Type keyType, Type valueType) implements Type {
}
