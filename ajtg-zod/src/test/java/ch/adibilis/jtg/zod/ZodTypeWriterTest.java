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
}
