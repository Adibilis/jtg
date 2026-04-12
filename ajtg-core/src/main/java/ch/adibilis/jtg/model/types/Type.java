package ch.adibilis.jtg.model.types;

public sealed interface Type permits PrimitiveType, ObjectType, ArrayType, MapType,
        OptionalType, EnumType, UnionType, LiteralType, TypeVar {
}
