package ch.adibilis.jtg.writer;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.types.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypeScriptTypeMapperTest {

    private final GeneratorConfig defaultConfig = new GeneratorConfig(
            List.of("com.example"), List.of("/out"), false, "", null, Map.of(), List.of(), 0
    );
    private final GeneratorConfig dateAsStringConfig = new GeneratorConfig(
            List.of("com.example"), List.of("/out"), true, "", null, Map.of(), List.of(), 0
    );

    @Test void mapsPrimitiveInt() { assertThat(TypeScriptTypeMapper.map(PrimitiveType.Int, defaultConfig)).isEqualTo("number"); }
    @Test void mapsPrimitiveDouble() { assertThat(TypeScriptTypeMapper.map(PrimitiveType.Double, defaultConfig)).isEqualTo("number"); }
    @Test void mapsPrimitiveBigInt() { assertThat(TypeScriptTypeMapper.map(PrimitiveType.BigInt, defaultConfig)).isEqualTo("bigint"); }
    @Test void mapsPrimitiveString() { assertThat(TypeScriptTypeMapper.map(PrimitiveType.String, defaultConfig)).isEqualTo("string"); }
    @Test void mapsPrimitiveBoolean() { assertThat(TypeScriptTypeMapper.map(PrimitiveType.Boolean, defaultConfig)).isEqualTo("boolean"); }
    @Test void mapsPrimitiveVoid() { assertThat(TypeScriptTypeMapper.map(PrimitiveType.Void, defaultConfig)).isEqualTo("void"); }
    @Test void mapsDateAsDate() { assertThat(TypeScriptTypeMapper.map(PrimitiveType.Date, defaultConfig)).isEqualTo("Date"); }
    @Test void mapsDateAsString() { assertThat(TypeScriptTypeMapper.map(PrimitiveType.Date, dateAsStringConfig)).isEqualTo("string"); }
    @Test void mapsPrimitiveFile() { assertThat(TypeScriptTypeMapper.map(PrimitiveType.File, defaultConfig)).isEqualTo("File"); }

    @Test void mapsArrayType() {
        assertThat(TypeScriptTypeMapper.map(new ArrayType(PrimitiveType.String), defaultConfig)).isEqualTo("string[]");
    }
    @Test void mapsNestedArrayType() {
        assertThat(TypeScriptTypeMapper.map(new ArrayType(new ArrayType(PrimitiveType.Int)), defaultConfig)).isEqualTo("number[][]");
    }
    @Test void mapsMapType() {
        assertThat(TypeScriptTypeMapper.map(new MapType(PrimitiveType.String, PrimitiveType.Int), defaultConfig)).isEqualTo("Record<string, number>");
    }
    @Test void mapsOptionalType() {
        assertThat(TypeScriptTypeMapper.map(new OptionalType(PrimitiveType.String), defaultConfig)).isEqualTo("string | null");
    }
    @Test void mapsEnumType() {
        assertThat(TypeScriptTypeMapper.map(new EnumType("Status", List.of("ACTIVE", "INACTIVE"), "common"), defaultConfig)).isEqualTo("'ACTIVE' | 'INACTIVE'");
    }
    @Test void mapsUnionType() {
        ObjectType dog = new ObjectType("Dog", "animals", List.of());
        ObjectType cat = new ObjectType("Cat", "animals", List.of());
        assertThat(TypeScriptTypeMapper.map(new UnionType("Animal", List.of(dog, cat), "animals", "type"), defaultConfig)).isEqualTo("Dog | Cat");
    }
    @Test void mapsObjectTypeWithoutGenerics() {
        ObjectType obj = new ObjectType("UserResponse", "user", List.of());
        assertThat(TypeScriptTypeMapper.map(obj, defaultConfig)).isEqualTo("UserResponse");
    }
    @Test void mapsObjectTypeWithGenericInstantiations() {
        ObjectType obj = new ObjectType("Response", "common", List.of("T"));
        ObjectType copy = obj.copy();
        copy.setGenericArgInstantiations(List.of(PrimitiveType.String));
        assertThat(TypeScriptTypeMapper.map(copy, defaultConfig)).isEqualTo("Response<string>");
    }
    @Test void mapsLiteralType() {
        assertThat(TypeScriptTypeMapper.map(new LiteralType("dog"), defaultConfig)).isEqualTo("'dog'");
    }
    @Test void mapsTypeVar() {
        assertThat(TypeScriptTypeMapper.map(new TypeVar("T"), defaultConfig)).isEqualTo("T");
    }
}
