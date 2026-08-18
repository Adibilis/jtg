package ch.adibilis.jtg.zod;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.endpoints.Endpoint;
import ch.adibilis.jtg.model.types.*;
import ch.adibilis.jtg.validation.Validation;
import ch.adibilis.jtg.writer.GeneratorContext;
import ch.adibilis.jtg.writer.TypeScriptFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ZodTypeWriterTest {

    private GeneratorConfig config;

    @BeforeEach
    void setUp() {
        config = new GeneratorConfig(
                List.of("com.example"), List.of("/out"), false, "", null, Map.of(), List.of(), 0
        );
    }

    @Test
    void generatesZodSchemaForValidatedType() {
        ObjectType obj = new ObjectType("UserForm", "user", List.of());
        obj.setFields(List.of(
                new Field("name", PrimitiveType.String, true, List.of(
                        new Validation.Size(1, 100, ""),
                        new Validation.NotBlank("")
                )),
                new Field("age", PrimitiveType.Int, true, List.of(
                        new Validation.Min(0, "must be positive"),
                        new Validation.Max(150, "")
                )),
                new Field("email", PrimitiveType.String, true, List.of(
                        new Validation.Email("invalid email")
                ))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("UserForm", obj);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        ZodTypeWriter writer = new ZodTypeWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        TypeScriptFile file = files.stream()
                .filter(f -> f.getRelativePath().contains("UserForm"))
                .findFirst().orElseThrow();

        assertThat(file.getBody()).contains("export const UserFormModel = z.object({");
        assertThat(file.getBody()).contains("name: z.string().min(1).max(100).regex(/.+/)");
        assertThat(file.getBody()).contains("age: z.number().int().min(0, { message: \"must be positive\" }).max(150)");
        assertThat(file.getBody()).contains("email: z.string().email({ message: \"invalid email\" })");
        assertThat(file.getBody()).contains("export type UserForm = z.infer<typeof UserFormModel>;");
    }

    @Test
    void mapsLocalDateFieldLikeDateToZodString() {
        // LocalDate is TS-typed the same as Date; Zod has no date-only primitive so it
        // continues to validate as a plain string, same as Date/LocalDateTime/Instant.
        ObjectType obj = new ObjectType("InvoiceForm", "invoice", List.of());
        obj.setFields(List.of(
                new Field("dueDate", PrimitiveType.LocalDate, true, List.of(
                        new Validation.NotBlank("")
                ))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("InvoiceForm", obj);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        ZodTypeWriter writer = new ZodTypeWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        TypeScriptFile file = files.stream()
                .filter(f -> f.getRelativePath().contains("InvoiceForm"))
                .findFirst().orElseThrow();

        assertThat(file.getBody()).contains("dueDate: z.string().regex(/.+/)");
    }

    @Test
    void delegatesNonValidatedTypesToPlainTypeScript() {
        ObjectType plain = new ObjectType("PlainDto", "common", List.of());
        plain.setFields(List.of(
                new Field("name", PrimitiveType.String),
                new Field("count", PrimitiveType.Int)
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("PlainDto", plain);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        ZodTypeWriter writer = new ZodTypeWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        TypeScriptFile file = files.stream()
                .filter(f -> f.getRelativePath().contains("PlainDto"))
                .findFirst().orElseThrow();

        // Should be a plain interface, not a Zod schema
        assertThat(file.getBody()).contains("export default interface PlainDto {");
        assertThat(file.getBody()).doesNotContain("z.object");
    }

    @Test
    void generatesZodEnum() {
        EnumType status = new EnumType("Status", List.of("ACTIVE", "INACTIVE"), "common");

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("Status", status);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        ZodTypeWriter writer = new ZodTypeWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        TypeScriptFile file = files.stream()
                .filter(f -> f.getRelativePath().contains("Status"))
                .findFirst().orElseThrow();

        assertThat(file.getBody()).contains("z.enum(['ACTIVE', 'INACTIVE'])");
    }

    @Test
    void handlesOptionalNullableFields() {
        ObjectType obj = new ObjectType("Form", "common", List.of());
        obj.setFields(List.of(
                new Field("nickname", PrimitiveType.String, false, List.of(
                        new Validation.Size(0, 50, "")
                ))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("Form", obj);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        ZodTypeWriter writer = new ZodTypeWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        TypeScriptFile file = files.get(0);
        assertThat(file.getBody()).contains("nickname: z.string().min(0).max(50).optional().nullable()");
    }

    @Test
    void mapsPatternValidation() {
        ObjectType obj = new ObjectType("CodeForm", "common", List.of());
        obj.setFields(List.of(
                new Field("code", PrimitiveType.String, true, List.of(
                        new Validation.Pattern("^[A-Z]{2}\\d{4}$", "")
                ))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("CodeForm", obj);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        ZodTypeWriter writer = new ZodTypeWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        assertThat(files.get(0).getBody()).contains("code: z.string().regex(/^[A-Z]{2}\\d{4}$/)");
    }

    @Test
    void mapsArrayAndMapTypes() {
        ObjectType obj = new ObjectType("Container", "common", List.of());
        obj.setFields(List.of(
                new Field("items", new ArrayType(PrimitiveType.String), true, List.of(
                        new Validation.Size(1, 10, "")
                )),
                new Field("meta", new MapType(PrimitiveType.String, PrimitiveType.Int), true, List.of())
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("Container", obj);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        ZodTypeWriter writer = new ZodTypeWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        assertThat(files.get(0).getBody()).contains("items: z.array(z.string()).min(1).max(10)");
        assertThat(files.get(0).getBody()).contains("meta: z.record(z.string(), z.number())");
    }

    @Test
    void mapsUnknownPrimitive() {
        ObjectType obj = new ObjectType("Form", "common", List.of());
        obj.setFields(List.of(
                new Field("name", PrimitiveType.String, true, List.of(new Validation.NotBlank(""))),
                new Field("payload", PrimitiveType.Unknown, true, List.of()),
                new Field("items", new ArrayType(PrimitiveType.Unknown), true, List.of())
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("Form", obj);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        TypeScriptFile file = new ZodTypeWriter().generate(ctx).getFirst();
        assertThat(file.getBody()).contains("payload: z.unknown()");
        assertThat(file.getBody()).contains("items: z.array(z.unknown())");
    }

    @Test
    void handlesTypesTrue() {
        assertThat(new ZodTypeWriter().handlesTypes()).isTrue();
    }

    @Test
    void zodObjectFileImportsZFromZod() {
        ObjectType obj = new ObjectType("Form", "common", List.of());
        obj.setFields(List.of(
                new Field("name", PrimitiveType.String, true, List.of(new Validation.NotBlank("")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("Form", obj);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        TypeScriptFile file = new ZodTypeWriter().generate(ctx).getFirst();
        assertThat(file.getBody()).contains("import { z } from 'zod';");
    }

    @Test
    void zodEnumFileImportsZFromZod() {
        EnumType status = new EnumType("Status", List.of("ACTIVE"), "common");

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("Status", status);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        TypeScriptFile file = new ZodTypeWriter().generate(ctx).getFirst();
        assertThat(file.getBody()).contains("import { z } from 'zod';");
    }

    @Test
    void zodObjectImportsReferencedZodModel() {
        ObjectType address = new ObjectType("Address", "common", List.of());
        address.setFields(List.of(
                new Field("street", PrimitiveType.String, true, List.of(new Validation.NotBlank("")))
        ));

        ObjectType lead = new ObjectType("Lead", "lead", List.of());
        lead.setFields(List.of(
                new Field("address", address, true, List.of(new Validation.NotBlank("")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("Address", address);
        types.put("Lead", lead);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        TypeScriptFile leadFile = new ZodTypeWriter().generate(ctx).stream()
                .filter(f -> f.getRelativePath().endsWith("Lead.ts"))
                .findFirst().orElseThrow();

        assertThat(leadFile.getBody()).contains("AddressModel");
        assertThat(leadFile.getBody()).contains("import { AddressModel } from");
        // Cross-package import path is relative
        assertThat(leadFile.getBody()).contains("../common/Address");
    }

    @Test
    void plainTypeImportsZodEnumThatItReferences() {
        EnumType status = new EnumType("Status", List.of("ACTIVE", "INACTIVE"), "common");
        ObjectType req = new ObjectType("UpdateRequest", "request", List.of());
        req.setFields(List.of(new Field("status", status)));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("Status", status);
        types.put("UpdateRequest", req);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        TypeScriptFile reqFile = new ZodTypeWriter().generate(ctx).stream()
                .filter(f -> f.getRelativePath().endsWith("UpdateRequest.ts"))
                .findFirst().orElseThrow();

        // Plain interface (UpdateRequest has no validations) gets a named import
        // for the Zod-emitted enum, pointing at types/common/Status.ts.
        assertThat(reqFile.getBody()).contains("import { Status } from");
        assertThat(reqFile.getBody()).contains("common/Status");
        assertThat(reqFile.getBody()).contains("status: Status;");
    }

    @Test
    void plainTypeImportsValidatedZodObjectItReferences() {
        ObjectType address = new ObjectType("Address", "common", List.of());
        address.setFields(List.of(
                new Field("street", PrimitiveType.String, true, List.of(new Validation.NotBlank("")))
        ));
        ObjectType contact = new ObjectType("Contact", "contact", List.of());
        // Contact itself has no validations -> stays as a plain interface
        contact.setFields(List.of(new Field("address", address)));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("Address", address);
        types.put("Contact", contact);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        TypeScriptFile contactFile = new ZodTypeWriter().generate(ctx).stream()
                .filter(f -> f.getRelativePath().endsWith("Contact.ts"))
                .findFirst().orElseThrow();

        assertThat(contactFile.getBody()).contains("import { Address } from");
        assertThat(contactFile.getBody()).contains("common/Address");
    }

    @Test
    void zodObjectImportsZodModelReferencedInsideArray() {
        ObjectType item = new ObjectType("Item", "common", List.of());
        item.setFields(List.of(
                new Field("name", PrimitiveType.String, true, List.of(new Validation.NotBlank("")))
        ));

        ObjectType bag = new ObjectType("Bag", "common", List.of());
        bag.setFields(List.of(
                new Field("items", new ArrayType(item), true, List.of(new Validation.Size(1, 10, "")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("Item", item);
        types.put("Bag", bag);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        TypeScriptFile bagFile = new ZodTypeWriter().generate(ctx).stream()
                .filter(f -> f.getRelativePath().endsWith("Bag.ts"))
                .findFirst().orElseThrow();

        assertThat(bagFile.getBody()).contains("import { ItemModel } from");
        assertThat(bagFile.getBody()).contains("z.array(ItemModel)");
    }

    // --- Regression: the real parser keys namedTypes by FULLY-QUALIFIED name
    // (SpringReflectionParser.typeCache uses clazz.getName()), not simple name.
    // The writer used to look up by simple name, so resolveModelImports never ran
    // in production and every nested-DTO schema referenced an unimported symbol.

    @Test
    void resolvesModelImportsWhenNamedTypesAreKeyedByFullyQualifiedName() {
        ObjectType address = new ObjectType("AddressDto", "dto", List.of());
        address.setFields(List.of(
                new Field("street", PrimitiveType.String, true, List.of(new Validation.NotBlank("")))
        ));

        ObjectType contact = new ObjectType("ContactRequest", "dto/contact", List.of());
        contact.setFields(List.of(
                new Field("address", address, false, List.of(new Validation.NotBlank("")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("ch.adibilis.dto.AddressDto", address);
        types.put("ch.adibilis.dto.contact.ContactRequest", contact);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        TypeScriptFile contactFile = new ZodTypeWriter().generate(ctx).stream()
                .filter(f -> f.getRelativePath().endsWith("ContactRequest.ts"))
                .findFirst().orElseThrow();

        assertThat(contactFile.getBody()).contains("AddressDtoModel");
        assertThat(contactFile.getBody()).contains("import { AddressDtoModel } from");
    }

    @Test
    void arrayOfValidatedObjectsAlsoGetsItsModelImport() {
        ObjectType line = new ObjectType("LineRequest", "dto", List.of());
        line.setFields(List.of(
                new Field("sku", PrimitiveType.String, true, List.of(new Validation.NotBlank("")))
        ));

        ObjectType order = new ObjectType("OrderRequest", "dto", List.of());
        order.setFields(List.of(
                new Field("lines", new ArrayType(line), false, List.of(new Validation.Size(1, Integer.MAX_VALUE, "")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("com.example.dto.LineRequest", line);
        types.put("com.example.dto.OrderRequest", order);
        GeneratorContext ctx = new GeneratorContext(List.of(), types, config);

        TypeScriptFile orderFile = new ZodTypeWriter().generate(ctx).stream()
                .filter(f -> f.getRelativePath().endsWith("OrderRequest.ts"))
                .findFirst().orElseThrow();

        assertThat(orderFile.getBody()).contains("import { LineRequestModel } from");
    }

    // --- Regression: presence constraints must beat @Nullable.
    // Field.required() is derived from @Nullable alone, but base DTOs carry
    // `@NotBlank private @Nullable String firstname` — @Nullable is there for
    // IDE null-analysis, @NotBlank is the contract. Emitting .optional() made the
    // schema fail OPEN: parse({}) succeeded on a required field.

    @Test
    void notBlankFieldIsNotOptionalEvenWhenNullableFlagsItNotRequired() {
        ObjectType dto = new ObjectType("ContactRequest", "dto", List.of());
        dto.setFields(List.of(
                new Field("firstname", PrimitiveType.String, false, List.of(new Validation.NotBlank("")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("ch.adibilis.dto.ContactRequest", dto);

        TypeScriptFile file = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config))
                .stream().filter(f -> f.getRelativePath().endsWith("ContactRequest.ts"))
                .findFirst().orElseThrow();

        assertThat(file.getBody()).contains("firstname: z.string().regex(/.+/),");
        assertThat(file.getBody()).doesNotContain("firstname: z.string().regex(/.+/).optional()");
    }

    @Test
    void notNullFieldIsRequiredAndAddsNoChain() {
        ObjectType dto = new ObjectType("ContactRequest", "dto", List.of());
        dto.setFields(List.of(
                new Field("salutation", PrimitiveType.String, false, List.of(new Validation.NotNull("")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("ch.adibilis.dto.ContactRequest", dto);

        TypeScriptFile file = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config))
                .stream().filter(f -> f.getRelativePath().endsWith("ContactRequest.ts"))
                .findFirst().orElseThrow();

        assertThat(file.getBody()).contains("salutation: z.string(),");
    }

    @Test
    void notEmptyCollectionIsRequiredAndConstrainedToAtLeastOne() {
        ObjectType dto = new ObjectType("OrderRequest", "dto", List.of());
        dto.setFields(List.of(
                new Field("lines", new ArrayType(PrimitiveType.String), false, List.of(new Validation.NotEmpty("")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("ch.adibilis.dto.OrderRequest", dto);

        TypeScriptFile file = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config))
                .stream().filter(f -> f.getRelativePath().endsWith("OrderRequest.ts"))
                .findFirst().orElseThrow();

        assertThat(file.getBody()).contains("lines: z.array(z.string()).min(1),");
    }

    @Test
    void fieldWithNoPresenceConstraintStaysOptional() {
        ObjectType dto = new ObjectType("PatchRequest", "dto", List.of());
        dto.setFields(List.of(
                new Field("note", PrimitiveType.String, false, List.of(new Validation.Size(0, 50, ""))),
                new Field("name", PrimitiveType.String, true, List.of(new Validation.NotBlank("")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("ch.adibilis.dto.PatchRequest", dto);

        TypeScriptFile file = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config))
                .stream().filter(f -> f.getRelativePath().endsWith("PatchRequest.ts"))
                .findFirst().orElseThrow();

        assertThat(file.getBody()).contains(".optional().nullable()");
    }

    // --- Cosmetic: @Size(min=1) with no max defaults to Integer.MAX_VALUE,
    // which leaked into output as .max(2147483647).

    @Test
    void suppressesTheIntegerMaxValueUpperBoundFromSizeDefaults() {
        ObjectType dto = new ObjectType("OrderRequest", "dto", List.of());
        dto.setFields(List.of(
                new Field("lines", new ArrayType(PrimitiveType.String), true,
                        List.of(new Validation.Size(1, Integer.MAX_VALUE, "")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("ch.adibilis.dto.OrderRequest", dto);

        TypeScriptFile file = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config))
                .stream().filter(f -> f.getRelativePath().endsWith("OrderRequest.ts"))
                .findFirst().orElseThrow();

        assertThat(file.getBody()).doesNotContain("2147483647");
        assertThat(file.getBody()).contains("lines: z.array(z.string()).min(1),");
    }

    // --- Regression: the Zod subtree must be CLOSED UNDER REFERENCE.
    // zodType() emits `<Name>Model` for any ObjectType, but a referenced DTO with
    // no constraints of its own used to land in the plain-TS bucket, where no
    // `<Name>Model` symbol exists. Such types are promoted to constraint-free
    // Zod schemas instead.

    @Test
    void promotesAnUnvalidatedObjectReferencedByAValidatedSchema() {
        ObjectType value = new ObjectType("AttributeValueRequest", "dto", List.of());
        value.setFields(List.of(new Field("key", PrimitiveType.String)));

        ObjectType contact = new ObjectType("ContactRequest", "dto", List.of());
        contact.setFields(List.of(
                new Field("name", PrimitiveType.String, true, List.of(new Validation.NotBlank(""))),
                new Field("attributeValues", new ArrayType(value), false, List.of())
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("ch.adibilis.dto.AttributeValueRequest", value);
        types.put("ch.adibilis.dto.ContactRequest", contact);
        List<TypeScriptFile> files = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config));

        TypeScriptFile valueFile = files.stream()
                .filter(f -> f.getRelativePath().endsWith("AttributeValueRequest.ts"))
                .findFirst().orElseThrow();
        assertThat(valueFile.getBody()).contains("export const AttributeValueRequestModel = z.object({");

        TypeScriptFile contactFile = files.stream()
                .filter(f -> f.getRelativePath().endsWith("ContactRequest.ts"))
                .findFirst().orElseThrow();
        assertThat(contactFile.getBody()).contains("import { AttributeValueRequestModel } from");
    }

    @Test
    void promotionIsTransitiveThroughAnUnvalidatedIntermediate() {
        ObjectType leaf = new ObjectType("Leaf", "dto", List.of());
        leaf.setFields(List.of(new Field("v", PrimitiveType.String)));

        ObjectType middle = new ObjectType("Middle", "dto", List.of());
        middle.setFields(List.of(new Field("leaf", leaf)));

        ObjectType root = new ObjectType("Root", "dto", List.of());
        root.setFields(List.of(
                new Field("name", PrimitiveType.String, true, List.of(new Validation.NotBlank(""))),
                new Field("middle", middle)
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("d.Leaf", leaf);
        types.put("d.Middle", middle);
        types.put("d.Root", root);
        List<TypeScriptFile> files = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config));

        for (String name : List.of("Leaf", "Middle", "Root")) {
            TypeScriptFile f = files.stream()
                    .filter(x -> x.getRelativePath().endsWith(name + ".ts"))
                    .findFirst().orElseThrow();
            assertThat(f.getBody()).as(name + " is a zod schema").contains("Model = z.object({");
        }
    }

    @Test
    void selfReferentialTypeDoesNotLoopForever() {
        ObjectType node = new ObjectType("FormSectionRequest", "dto", List.of());
        node.setFields(List.of(
                new Field("title", PrimitiveType.String, true, List.of(new Validation.NotBlank("")))
        ));
        // cycle: a section contains sections
        node.setFields(List.of(
                new Field("title", PrimitiveType.String, true, List.of(new Validation.NotBlank(""))),
                new Field("children", new ArrayType(node), false, List.of())
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("ch.adibilis.dto.FormSectionRequest", node);

        List<TypeScriptFile> files = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config));
        assertThat(files).isNotEmpty();
    }

    @Test
    void unreferencedUnvalidatedTypeStaysPlainTypeScript() {
        ObjectType plain = new ObjectType("PlainResponse", "dto", List.of());
        plain.setFields(List.of(new Field("v", PrimitiveType.String)));

        ObjectType validated = new ObjectType("SomeRequest", "dto", List.of());
        validated.setFields(List.of(
                new Field("name", PrimitiveType.String, true, List.of(new Validation.NotBlank("")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("d.PlainResponse", plain);
        types.put("d.SomeRequest", validated);
        List<TypeScriptFile> files = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config));

        TypeScriptFile plainFile = files.stream()
                .filter(f -> f.getRelativePath().endsWith("PlainResponse.ts"))
                .findFirst().orElseThrow();
        assertThat(plainFile.getBody()).contains("export default interface PlainResponse {");
        assertThat(plainFile.getBody()).doesNotContain("z.object");
    }

    // --- Regression: a self-referential schema cannot use `z.infer` off its own
    // initializer — TypeScript reports TS7022/TS2448. Zod's idiom is z.lazy() plus an
    // explicit z.ZodType<T> annotation against a declared interface.

    @Test
    void selfReferentialSchemaUsesLazyAndAnExplicitInterface() {
        ObjectType option = new ObjectType("OptionRequest", "dto", List.of());
        option.setFields(List.of(
                new Field("label", PrimitiveType.String, true, List.of(new Validation.NotBlank(""))),
                new Field("children", new ArrayType(option), false, List.of())
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("ch.adibilis.dto.OptionRequest", option);

        TypeScriptFile file = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config))
                .stream().filter(f -> f.getRelativePath().endsWith("OptionRequest.ts"))
                .findFirst().orElseThrow();

        String body = file.getBody();
        assertThat(body).contains("export interface OptionRequest {");
        assertThat(body).contains("children?: OptionRequest[] | null;");
        assertThat(body).contains("export const OptionRequestModel: z.ZodType<OptionRequest> = z.lazy(() => z.object({");
        // the self-referential inference that TS rejects must be gone
        assertThat(body).doesNotContain("export type OptionRequest = z.infer<");
    }

    @Test
    void nonRecursiveSchemaKeepsThePlainInferForm() {
        ObjectType dto = new ObjectType("SimpleRequest", "dto", List.of());
        dto.setFields(List.of(
                new Field("name", PrimitiveType.String, true, List.of(new Validation.NotBlank("")))
        ));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("ch.adibilis.dto.SimpleRequest", dto);

        String body = new ZodTypeWriter().generate(new GeneratorContext(List.of(), types, config))
                .stream().filter(f -> f.getRelativePath().endsWith("SimpleRequest.ts"))
                .findFirst().orElseThrow().getBody();

        assertThat(body).contains("export const SimpleRequestModel = z.object({");
        assertThat(body).contains("export type SimpleRequest = z.infer<typeof SimpleRequestModel>;");
        assertThat(body).doesNotContain("z.lazy");
    }
}
