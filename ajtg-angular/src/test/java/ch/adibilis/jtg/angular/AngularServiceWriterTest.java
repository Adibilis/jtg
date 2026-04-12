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
        assertThat(body).contains("let params = new HttpParams();");
        assertThat(body).contains("params = params.append('filter', filter);");
        assertThat(body).contains("if (sort)");
        assertThat(body).contains("params = params.append('sort', sort);");
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

        String body = files.get(0).getBody();
        assertThat(body).contains("after.toISOString()");
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
    void generatesArrayParamWithReduce() {
        Endpoint ep = makeEndpoint("SearchController", "search", HttpMethod.GET,
                "/api/search", PrimitiveType.String);
        ep.getParams().add(new Field("tags", new ArrayType(PrimitiveType.String), true));

        GeneratorContext ctx = new GeneratorContext(List.of(ep), Map.of(), config);

        AngularServiceWriter writer = new AngularServiceWriter();
        List<TypeScriptFile> files = writer.generate(ctx);

        String body = files.get(0).getBody();
        assertThat(body).contains(".reduce(");
        assertThat(body).contains("p.append('tags', item)");
    }
}
