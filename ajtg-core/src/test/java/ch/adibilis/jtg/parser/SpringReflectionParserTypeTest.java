package ch.adibilis.jtg.parser;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.types.*;
import ch.adibilis.jtg.parser.fixtures.*;
import ch.adibilis.jtg.validation.Validation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class SpringReflectionParserTypeTest {

    private SpringReflectionParser parser;

    @BeforeEach
    void setUp() {
        GeneratorConfig config = new GeneratorConfig(
                List.of("ch.adibilis.jtg.parser.fixtures"),
                List.of("/out"), false, "", null, Map.of(), List.of(), 0
        );
        parser = new SpringReflectionParser(config);
    }

    // --- Primitive types ---

    @Test void parsesInt() { assertThat(parser.resolveType(int.class)).isEqualTo(PrimitiveType.Int); }
    @Test void parsesInteger() { assertThat(parser.resolveType(Integer.class)).isEqualTo(PrimitiveType.Int); }
    @Test void parsesLong() { assertThat(parser.resolveType(long.class)).isEqualTo(PrimitiveType.Int); }
    @Test void parsesLongWrapper() { assertThat(parser.resolveType(Long.class)).isEqualTo(PrimitiveType.Int); }
    @Test void parsesShort() { assertThat(parser.resolveType(short.class)).isEqualTo(PrimitiveType.Int); }
    @Test void parsesByte() { assertThat(parser.resolveType(byte.class)).isEqualTo(PrimitiveType.Int); }
    @Test void parsesFloat() { assertThat(parser.resolveType(float.class)).isEqualTo(PrimitiveType.Double); }
    @Test void parsesDouble() { assertThat(parser.resolveType(double.class)).isEqualTo(PrimitiveType.Double); }
    @Test void parsesDoubleWrapper() { assertThat(parser.resolveType(Double.class)).isEqualTo(PrimitiveType.Double); }
    @Test void parsesNumber() { assertThat(parser.resolveType(Number.class)).isEqualTo(PrimitiveType.Double); }
    @Test void parsesBigInteger() { assertThat(parser.resolveType(BigInteger.class)).isEqualTo(PrimitiveType.BigInt); }
    @Test void parsesString() { assertThat(parser.resolveType(String.class)).isEqualTo(PrimitiveType.String); }
    @Test void parsesBoolean() { assertThat(parser.resolveType(boolean.class)).isEqualTo(PrimitiveType.Boolean); }
    @Test void parsesBooleanWrapper() { assertThat(parser.resolveType(Boolean.class)).isEqualTo(PrimitiveType.Boolean); }
    @Test void parsesVoid() { assertThat(parser.resolveType(void.class)).isEqualTo(PrimitiveType.Void); }
    @Test void parsesVoidWrapper() { assertThat(parser.resolveType(Void.class)).isEqualTo(PrimitiveType.Void); }
    @Test void parsesDate() { assertThat(parser.resolveType(Date.class)).isEqualTo(PrimitiveType.Date); }
    @Test void parsesLocalDate() { assertThat(parser.resolveType(LocalDate.class)).isEqualTo(PrimitiveType.Date); }
    @Test void parsesLocalDateTime() { assertThat(parser.resolveType(LocalDateTime.class)).isEqualTo(PrimitiveType.Date); }
    @Test void parsesInstant() { assertThat(parser.resolveType(Instant.class)).isEqualTo(PrimitiveType.Date); }

    // --- Arrays ---

    @Test
    void parsesNativeArray() {
        Type result = parser.resolveType(int[].class);
        assertThat(result).isInstanceOf(ArrayType.class);
        assertThat(((ArrayType) result).subType()).isEqualTo(PrimitiveType.Int);
    }

    @Test
    void parsesObjectArray() {
        Type result = parser.resolveType(String[].class);
        assertThat(result).isInstanceOf(ArrayType.class);
        assertThat(((ArrayType) result).subType()).isEqualTo(PrimitiveType.String);
    }

    // --- Enum ---

    @Test
    void parsesEnum() {
        Type result = parser.resolveType(StatusEnum.class);
        assertThat(result).isInstanceOf(EnumType.class);
        EnumType e = (EnumType) result;
        assertThat(e.name()).isEqualTo("StatusEnum");
        assertThat(e.values()).containsExactly("ACTIVE", "INACTIVE", "PENDING");
    }

    // --- Simple ObjectType ---

    @Test
    void parsesSimpleObject() {
        Type result = parser.resolveType(SimpleDto.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType obj = (ObjectType) result;
        assertThat(obj.getName()).isEqualTo("SimpleDto");
        assertThat(obj.getFields()).hasSize(3);
        assertThat(obj.getFields().get(0).name()).isEqualTo("name");
        assertThat(obj.getFields().get(0).type()).isEqualTo(PrimitiveType.String);
        assertThat(obj.getFields().get(1).name()).isEqualTo("age");
        assertThat(obj.getFields().get(1).type()).isEqualTo(PrimitiveType.Int);
        assertThat(obj.getFields().get(2).name()).isEqualTo("active");
        assertThat(obj.getFields().get(2).type()).isEqualTo(PrimitiveType.Boolean);
    }

    // --- Generic ObjectType ---

    @Test
    void parsesGenericObject() {
        Type result = parser.resolveType(GenericDto.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType obj = (ObjectType) result;
        assertThat(obj.getGenericParams()).containsExactly("T");
        assertThat(obj.getFields()).hasSize(2);
        assertThat(obj.getFields().get(0).type()).isInstanceOf(TypeVar.class);
        assertThat(((TypeVar) obj.getFields().get(0).type()).name()).isEqualTo("T");
    }

    // --- Collections ---

    @Test
    void parsesObjectWithCollectionFields() {
        Type result = parser.resolveType(NestedGenericDto.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType obj = (ObjectType) result;
        // List<String> tags
        assertThat(obj.getFields().get(0).type()).isInstanceOf(ArrayType.class);
        assertThat(((ArrayType) obj.getFields().get(0).type()).subType()).isEqualTo(PrimitiveType.String);
        // Map<String, Integer> scores
        assertThat(obj.getFields().get(1).type()).isInstanceOf(MapType.class);
        // Optional<String> nickname
        assertThat(obj.getFields().get(2).type()).isInstanceOf(OptionalType.class);
        // List<List<Integer>> matrix
        Type matrix = obj.getFields().get(3).type();
        assertThat(matrix).isInstanceOf(ArrayType.class);
        assertThat(((ArrayType) matrix).subType()).isInstanceOf(ArrayType.class);
    }

    // --- Circular references ---

    @Test
    void handlesCircularReferences() {
        Type result = parser.resolveType(CircularA.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType a = (ObjectType) result;
        assertThat(a.getFields()).hasSize(2);
        ObjectType b = (ObjectType) a.getFields().get(1).type();
        assertThat(b.getName()).isEqualTo("CircularB");
        // CircularB.back should reference the same cached CircularA
        assertThat(b.getFields().get(1).type()).isSameAs(a);
    }

    // --- Superclass with generic propagation ---

    @Test
    void parsesSuperclassWithGenericBinding() {
        Type result = parser.resolveType(ChildDto.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType child = (ObjectType) result;
        // Should have own field 'extra' + inherited 'data' (bound to String) + inherited 'count'
        assertThat(child.getFields()).hasSize(3);
        Field dataField = child.getFields().stream()
                .filter(f -> f.name().equals("data")).findFirst().orElseThrow();
        assertThat(dataField.type()).isEqualTo(PrimitiveType.String);
    }

    // --- JsonSubTypes (UnionType) ---

    @Test
    void parsesJsonSubTypesAsUnion() {
        Type result = parser.resolveType(Animal.class);
        assertThat(result).isInstanceOf(UnionType.class);
        UnionType union = (UnionType) result;
        assertThat(union.name()).isEqualTo("Animal");
        assertThat(union.variants()).hasSize(2);
        assertThat(union.variants().get(0).getName()).isEqualTo("Dog");
        assertThat(union.variants().get(1).getName()).isEqualTo("Cat");
        assertThat(union.discriminatorProperty()).isEqualTo("type");
    }

    // --- JsonNaming ---

    @Test
    void appliesJsonNamingStrategy() {
        Type result = parser.resolveType(SnakeCaseDto.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType obj = (ObjectType) result;
        assertThat(obj.getFields().get(0).name()).isEqualTo("first_name");
        assertThat(obj.getFields().get(1).name()).isEqualTo("last_name");
    }

    // --- JsonProperty ---

    @Test
    void appliesJsonPropertyOverride() {
        Type result = parser.resolveType(JsonPropertyDto.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType obj = (ObjectType) result;
        assertThat(obj.getFields().get(0).name()).isEqualTo("display_name");
        assertThat(obj.getFields().get(1).name()).isEqualTo("value");
    }

    // --- Validation constraints ---

    @Test
    void extractsValidationConstraints() {
        Type result = parser.resolveType(ValidatedDto.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType obj = (ObjectType) result;

        Field score = obj.getFields().stream().filter(f -> f.name().equals("score")).findFirst().orElseThrow();
        assertThat(score.validations()).hasSize(2);
        assertThat(score.validations().get(0)).isInstanceOf(Validation.Min.class);
        assertThat(((Validation.Min) score.validations().get(0)).value()).isEqualTo(0);
        assertThat(((Validation.Min) score.validations().get(0)).message()).isEqualTo("must be positive");

        Field name = obj.getFields().stream().filter(f -> f.name().equals("name")).findFirst().orElseThrow();
        assertThat(name.validations().stream().filter(v -> v instanceof Validation.Size).findFirst()).isPresent();
        assertThat(name.validations().stream().filter(v -> v instanceof Validation.NotBlank).findFirst()).isPresent();

        Field email = obj.getFields().stream().filter(f -> f.name().equals("email")).findFirst().orElseThrow();
        assertThat(email.validations().get(0)).isInstanceOf(Validation.Email.class);
        assertThat(((Validation.Email) email.validations().get(0)).message()).isEqualTo("invalid email");

        Field code = obj.getFields().stream().filter(f -> f.name().equals("code")).findFirst().orElseThrow();
        assertThat(code.validations().get(0)).isInstanceOf(Validation.Pattern.class);
    }

    // --- Nullable ---

    @Test
    void detectsNullableAnnotation() {
        Type result = parser.resolveType(NullableDto.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType obj = (ObjectType) result;
        Field required = obj.getFields().stream().filter(f -> f.name().equals("required")).findFirst().orElseThrow();
        assertThat(required.required()).isTrue();
        Field optional = obj.getFields().stream().filter(f -> f.name().equals("optional")).findFirst().orElseThrow();
        assertThat(optional.required()).isFalse();
    }

    // --- JsonIgnore and static ---

    @Test
    void skipsIgnoredAndStaticFields() {
        Type result = parser.resolveType(IgnoredFieldsDto.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType obj = (ObjectType) result;
        assertThat(obj.getFields()).hasSize(1);
        assertThat(obj.getFields().get(0).name()).isEqualTo("visible");
    }

    // --- Inner class ---

    @Test
    void parsesInnerClassWithOuterClassPrefix() {
        Type result = parser.resolveType(OuterClass.Inner.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType obj = (ObjectType) result;
        assertThat(obj.getName()).isEqualTo("OuterClassInner");
    }

    @Test
    void innerClassesWithSameSimpleNameInDifferentOutersGetDistinctNames() {
        Type resultA = parser.resolveType(EntityA.Projection.class);
        Type resultB = parser.resolveType(EntityB.Projection.class);
        assertThat(resultA).isInstanceOf(ObjectType.class);
        assertThat(resultB).isInstanceOf(ObjectType.class);
        String nameA = ((ObjectType) resultA).getName();
        String nameB = ((ObjectType) resultB).getName();
        assertThat(nameA).isEqualTo("EntityAProjection");
        assertThat(nameB).isEqualTo("EntityBProjection");
        assertThat(nameA).isNotEqualTo(nameB);
    }

    // --- Array fields on objects ---

    @Test
    void parsesArrayFields() {
        Type result = parser.resolveType(ArrayFieldDto.class);
        assertThat(result).isInstanceOf(ObjectType.class);
        ObjectType obj = (ObjectType) result;
        assertThat(obj.getFields().get(0).type()).isInstanceOf(ArrayType.class);
        assertThat(((ArrayType) obj.getFields().get(0).type()).subType()).isEqualTo(PrimitiveType.Int);
    }

    // --- Package segment ---

    @Test
    void extractsPackageSegment() {
        Type result = parser.resolveType(SimpleDto.class);
        ObjectType obj = (ObjectType) result;
        // Base package is "ch.adibilis.jtg.parser.fixtures", SimpleDto is directly in it
        assertThat(obj.getPackageSegment()).isEqualTo("common");
    }

    // --- Custom type mapping ---

    @Test
    void appliesCustomTypeMapping() {
        GeneratorConfig config = new GeneratorConfig(
                List.of("ch.adibilis.jtg.parser.fixtures"),
                List.of("/out"), false, "", null,
                Map.of("SimpleDto", "string"), List.of(), 0
        );
        SpringReflectionParser customParser = new SpringReflectionParser(config);
        Type result = customParser.resolveType(SimpleDto.class);
        assertThat(result).isEqualTo(PrimitiveType.String);
    }
}
