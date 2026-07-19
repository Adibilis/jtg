package ch.adibilis.jtg.angular;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.endpoints.*;
import ch.adibilis.jtg.model.types.*;
import ch.adibilis.jtg.writer.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AngularServiceWriterTest {

    private GeneratorConfig config;

    @BeforeEach
    void setUp() {
        config = new GeneratorConfig(
                List.of("com.example"), List.of("/out"), false, "", null, Map.of(), List.of(), 0
        );
    }

    private Endpoint makeEndpoint(String className, String methodName, HttpMethod method,
                                   String url, Type returnType) {
        Endpoint ep = new Endpoint();
        ep.setClassName(className);
        ep.setMethodName(methodName);
        ep.setHttpMethod(method);
        ep.setUrl(url);
        ep.setReturnType(returnType);
        return ep;
    }

    @Test
    void generatesServiceClass() {
        ObjectType dto = new ObjectType("UserResponse", "user", List.of());
        Endpoint ep = makeEndpoint("UserController", "getUser", HttpMethod.GET,
                "/api/users/{id}", dto);
        ep.getUrlArgs().add(new Field("id", PrimitiveType.String));

        Map<String, Type> types = new LinkedHashMap<>();
        types.put("UserResponse", dto);
        GeneratorContext ctx = new GeneratorContext(List.of(ep), types, config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        assertThat(files).hasSize(1);
        TypeScriptFile file = files.get(0);
        assertThat(file.getRelativePath()).isEqualTo("endpoints/user.service.ts");
        assertThat(file.getBody()).contains("export class UserService {");
        assertThat(file.getBody()).contains("private http = inject(HttpClient);");
        assertThat(file.getBody()).contains("baseURL = environment.serverUrl;");
    }

    @Test
    void generatesGetMethod() {
        ObjectType dto = new ObjectType("UserResponse", "user", List.of());
        Endpoint ep = makeEndpoint("UserController", "getUser", HttpMethod.GET,
                "/api/users/{id}", dto);
        ep.getUrlArgs().add(new Field("id", PrimitiveType.String));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        String body = files.get(0).getBody();
        assertThat(body).contains("getUser(id: string): Observable<UserResponse>");
        assertThat(body).contains("return this.http.get<UserResponse>");
        assertThat(body).contains("${id}");
    }

    @Test
    void generatesPostMethodWithBody() {
        ObjectType dto = new ObjectType("UserResponse", "user", List.of());
        ObjectType req = new ObjectType("CreateUserRequest", "user", List.of());
        Endpoint ep = makeEndpoint("UserController", "createUser", HttpMethod.POST,
                "/api/users", dto);
        ep.setBody(req);

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        String body = files.get(0).getBody();
        assertThat(body).contains("createUser(body: CreateUserRequest): Observable<UserResponse>");
        assertThat(body).contains("this.http.post<UserResponse>");
        assertThat(body).contains("body");
    }

    @Test
    void generatesQueryParams() {
        Endpoint ep = makeEndpoint("UserController", "listUsers", HttpMethod.GET,
                "/api/users", new ArrayType(new ObjectType("UserResponse", "user", List.of())));
        ep.getParams().add(new Field("filter", PrimitiveType.String, true));
        ep.getParams().add(new Field("sort", PrimitiveType.String, false));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        String body = files.get(0).getBody();
        assertThat(body).contains("const params = toHttpParams({ filter, sort });");
        assertThat(body).contains("import { toHttpParams } from '../utils/http-params';");
    }

    @Test
    void spreadsObjectTypeQueryParamIntoToHttpParams() {
        ObjectType filterType = new ObjectType("ContactFilterRequest", "contact", List.of());
        filterType.setFields(List.of(new Field("search", PrimitiveType.String, false)));
        Endpoint ep = makeEndpoint("ContactController", "search", HttpMethod.GET,
                "/api/contact", new ArrayType(new ObjectType("ContactResponse", "contact", List.of())));
        ep.getParams().add(new Field("filter", filterType, true));
        ep.getParams().add(new Field("sort", PrimitiveType.String, false));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        String body = files.get(0).getBody();
        assertThat(body).contains("const params = toHttpParams({ ...filter, sort });");
    }

    @Test
    void emitsSharedHttpParamsUtilFileWhenAnyEndpointHasQueryParams() {
        Endpoint ep = makeEndpoint("UserController", "listUsers", HttpMethod.GET,
                "/api/users", new ArrayType(new ObjectType("UserResponse", "user", List.of())));
        ep.getParams().add(new Field("filter", PrimitiveType.String, true));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        TypeScriptFile utilFile = files.stream()
                .filter(f -> f.getRelativePath().equals("utils/http-params.ts"))
                .findFirst().orElseThrow();

        assertThat(utilFile.getBody()).contains("export function toHttpParams(obj: Record<string, unknown>): HttpParams");
        assertThat(utilFile.getBody()).contains("if (value === null || value === undefined) continue;");
        assertThat(utilFile.getBody()).contains("value instanceof Date");
        assertThat(utilFile.getBody()).contains("Array.isArray(value)");
    }

    @Test
    void doesNotEmitHttpParamsUtilFileWhenNoEndpointHasQueryParams() {
        Endpoint ep = makeEndpoint("UserController", "getUser", HttpMethod.GET,
                "/api/users/{id}", new ObjectType("UserResponse", "user", List.of()));
        ep.getUrlArgs().add(new Field("id", PrimitiveType.String));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        assertThat(files).extracting(TypeScriptFile::getRelativePath)
                .doesNotContain("utils/http-params.ts");
    }

    @Test
    void generatesVoidReturnType() {
        Endpoint ep = makeEndpoint("UserController", "deleteUser", HttpMethod.DELETE,
                "/api/users/{id}", PrimitiveType.Void);
        ep.getUrlArgs().add(new Field("id", PrimitiveType.String));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        String body = files.get(0).getBody();
        assertThat(body).contains("Observable<void>");
    }

    @Test
    void generatesFileUpload() {
        ObjectType dto = new ObjectType("FileResponse", "common", List.of());
        Endpoint ep = makeEndpoint("FileController", "upload", HttpMethod.POST,
                "/api/files", dto);
        ep.getFileParams().add(new Field("file", PrimitiveType.File));
        ep.getParams().add(new Field("description", PrimitiveType.String, true));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        String body = files.get(0).getBody();
        assertThat(body).contains("file: File");
        assertThat(body).contains("const formData = new FormData();");
        assertThat(body).contains("formData.append('file', file);");
        // No content-type header for file uploads
        assertThat(body).doesNotContain("headers");
    }

    @Test
    void generatesDateToISOString() {
        Endpoint ep = makeEndpoint("EventController", "listEvents", HttpMethod.GET,
                "/api/events", new ArrayType(PrimitiveType.String));
        ep.getParams().add(new Field("after", PrimitiveType.Date, true));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        // ISO serialization is centralized in the shared toHttpParams helper, not the call site.
        String body = files.get(0).getBody();
        assertThat(body).contains("const params = toHttpParams({ after });");

        TypeScriptFile utilFile = files.stream()
                .filter(f -> f.getRelativePath().equals("utils/http-params.ts"))
                .findFirst().orElseThrow();
        assertThat(utilFile.getBody()).contains("value.toISOString()");
    }

    @Test
    void usesKebabCaseFileName() {
        Endpoint ep = makeEndpoint("MediaUploadController", "upload", HttpMethod.POST,
                "/api/media", PrimitiveType.Void);

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        assertThat(files.get(0).getRelativePath()).contains("media-upload.service.ts");
    }

    @Test
    void usesConfigurableEnvironmentImportPath() {
        GeneratorConfig customConfig = new GeneratorConfig(
                List.of("com.example"), List.of("/out"), false, "",
                "../../env/environment", Map.of(), List.of(), 0
        );

        Endpoint ep = makeEndpoint("UserController", "getUser", HttpMethod.GET,
                "/api/users", PrimitiveType.String);

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), customConfig);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        assertThat(files.get(0).getBody()).contains("from '../../env/environment'");
    }

    @Test
    void generatesArrayParamViaToHttpParams() {
        Endpoint ep = makeEndpoint("SearchController", "search", HttpMethod.GET,
                "/api/search", PrimitiveType.String);
        ep.getParams().add(new Field("tags", new ArrayType(PrimitiveType.String), true));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        String body = files.get(0).getBody();
        assertThat(body).contains("const params = toHttpParams({ tags });");

        // Array-join semantics live in the shared helper, not the call site.
        TypeScriptFile utilFile = files.stream()
                .filter(f -> f.getRelativePath().equals("utils/http-params.ts"))
                .findFirst().orElseThrow();
        assertThat(utilFile.getBody()).contains("Array.isArray(value)");
    }

    // --- @RequestHeader ---

    @Test
    void generatesRequiredHeaderParam() {
        Endpoint ep = makeEndpoint("ContactController", "replace", HttpMethod.PUT,
                "/api/contact/{contactId}", new ObjectType("ContactResponse", "contact", List.of()));
        ep.getUrlArgs().add(new Field("contactId", PrimitiveType.Int));
        ep.setBody(new ObjectType("ContactRequest", "contact", List.of()));
        ep.getHeaders().add(new HeaderParam(new Field("ifMatch", PrimitiveType.String, true), "If-Match"));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        String body = writer.generate(ctx).get(0).getBody();

        assertThat(body).contains("replace(body: ContactRequest, contactId: number, ifMatch: string): Observable<ContactResponse>");
        assertThat(body).contains("const requestHeaders: Record<string, string> = { ...headers };");
        assertThat(body).contains("requestHeaders['If-Match'] = ifMatch;");
        assertThat(body).contains("{ headers: requestHeaders }");
    }

    @Test
    void generatesOptionalHeaderParam() {
        Endpoint ep = makeEndpoint("UserController", "getUser", HttpMethod.GET,
                "/api/users/{id}", new ObjectType("UserResponse", "user", List.of()));
        ep.getUrlArgs().add(new Field("id", PrimitiveType.String));
        ep.getHeaders().add(new HeaderParam(new Field("traceId", PrimitiveType.String, false), "X-Trace-Id"));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        String body = writer.generate(ctx).get(0).getBody();

        assertThat(body).contains("getUser(id: string, traceId?: string): Observable<UserResponse>");
        assertThat(body).contains("if (traceId) {");
        assertThat(body).contains("requestHeaders['X-Trace-Id'] = traceId;");
    }

    // --- ResponseEntity<T> "…WithResponse" ETag variant ---

    @Test
    void generatesWithResponseVariantForResponseEntityEndpoint() {
        Endpoint ep = makeEndpoint("ContactController", "replace", HttpMethod.PUT,
                "/api/contact/{contactId}", new ObjectType("ContactResponse", "contact", List.of()));
        ep.getUrlArgs().add(new Field("contactId", PrimitiveType.Int));
        ep.setBody(new ObjectType("ContactRequest", "contact", List.of()));
        ep.getHeaders().add(new HeaderParam(new Field("ifMatch", PrimitiveType.String, true), "If-Match"));
        ep.setResponseEntity(true);

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        String body = writer.generate(ctx).get(0).getBody();

        // Plain method still present, unwrapped return type, unbroken.
        assertThat(body).contains("replace(body: ContactRequest, contactId: number, ifMatch: string): Observable<ContactResponse>");
        // New response-observing variant.
        assertThat(body).contains("replaceWithResponse(body: ContactRequest, contactId: number, ifMatch: string): Observable<HttpResponse<ContactResponse>>");
        assertThat(body).contains("observe: 'response'");
        assertThat(body).contains("import { HttpClient, HttpResponse } from '@angular/common/http';");
    }

    @Test
    void doesNotGenerateWithResponseVariantWhenNotResponseEntity() {
        Endpoint ep = makeEndpoint("UserController", "getUser", HttpMethod.GET,
                "/api/users/{id}", new ObjectType("UserResponse", "user", List.of()));
        ep.getUrlArgs().add(new Field("id", PrimitiveType.String));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        String body = writer.generate(ctx).get(0).getBody();

        assertThat(body).doesNotContain("WithResponse");
        assertThat(body).doesNotContain("HttpResponse");
    }

    @Test
    void usesNamedImportAndCorrectPathForZodEmittedType() {
        // Endpoint returns an enum that lives in a Zod-emitted file: nested path,
        // no default export. The service file should reflect both.
        EnumType status = new EnumType("Status", List.of("OK", "BAD"), "common");
        Endpoint ep = makeEndpoint("StatusController", "getStatus", HttpMethod.GET,
                "/api/status", status);

        // Simulate what the Mojo populates: a type file at the Zod nested path
        // with hasDefaultExport=false.
        TypeScriptFile statusFile = new TypeScriptFile("types/common/Status.ts");
        statusFile.setHasDefaultExport(false);
        Map<String, TypeScriptFile> typeFiles = Map.of("Status", statusFile);

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config, typeFiles);

        AngularServiceWriter writer = new AngularServiceWriter();
        TypeScriptFile file = writer.generate(ctx).get(0);

        assertThat(file.getBody()).contains("import { Status } from '../types/common/Status';");
    }

    @Test
    void usesDefaultImportAndCorrectPathForPlainType() {
        // Endpoint returns a plain ObjectType: flat path, default export.
        ObjectType dto = new ObjectType("UserResponse", "user", List.of());
        Endpoint ep = makeEndpoint("UserController", "getUser", HttpMethod.GET,
                "/api/users/{id}", dto);
        ep.getUrlArgs().add(new Field("id", PrimitiveType.String));

        TypeScriptFile dtoFile = new TypeScriptFile("types/UserResponse.ts");
        dtoFile.setHasDefaultExport(true);
        Map<String, TypeScriptFile> typeFiles = Map.of("UserResponse", dtoFile);

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config, typeFiles);

        AngularServiceWriter writer = new AngularServiceWriter();
        TypeScriptFile file = writer.generate(ctx).get(0);

        assertThat(file.getBody()).contains("import UserResponse from '../types/UserResponse';");
    }

    @Test
    void fallsBackToLegacyFlatDefaultPathWhenTypeFilesEmpty() {
        // When no type writer ran first (or for an unknown type name),
        // the writer should keep the legacy `../types/<Name>` default import.
        ObjectType dto = new ObjectType("LegacyDto", "common", List.of());
        Endpoint ep = makeEndpoint("LegacyController", "get", HttpMethod.GET,
                "/api/legacy", dto);

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        TypeScriptFile file = writer.generate(ctx).get(0);

        assertThat(file.getBody()).contains("import LegacyDto from '../types/LegacyDto';");
    }
}
