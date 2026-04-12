package ch.adibilis.jtg.parser;

import ch.adibilis.jtg.config.GeneratorConfig;
import ch.adibilis.jtg.model.endpoints.*;
import ch.adibilis.jtg.model.types.*;
import ch.adibilis.jtg.parser.fixtures.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringReflectionParserEndpointTest {

    private SpringReflectionParser parser;

    @BeforeEach
    void setUp() {
        GeneratorConfig config = new GeneratorConfig(
                List.of("ch.adibilis.jtg.parser.fixtures"),
                List.of("/out"), false, "", null, Map.of(), List.of(), 0
        );
        parser = new SpringReflectionParser(config);
    }

    @Test
    void parsesGetEndpoint() {
        List<Endpoint> endpoints = parser.parseController(TestUserController.class);
        Endpoint get = endpoints.stream()
                .filter(e -> e.getMethodName().equals("getUser"))
                .findFirst().orElseThrow();

        assertThat(get.getHttpMethod()).isEqualTo(HttpMethod.GET);
        assertThat(get.getUrl()).isEqualTo("/api/users/{id}");
        assertThat(get.getUrlArgs()).hasSize(1);
        assertThat(get.getUrlArgs().get(0).name()).isEqualTo("id");
        assertThat(get.getReturnType()).isInstanceOf(ObjectType.class);
        assertThat(get.getClassName()).isEqualTo("TestUserController");
    }

    @Test
    void parsesPostEndpoint() {
        List<Endpoint> endpoints = parser.parseController(TestUserController.class);
        Endpoint post = endpoints.stream()
                .filter(e -> e.getMethodName().equals("createUser"))
                .findFirst().orElseThrow();

        assertThat(post.getHttpMethod()).isEqualTo(HttpMethod.POST);
        assertThat(post.getUrl()).isEqualTo("/api/users");
        assertThat(post.getBody()).isInstanceOf(ObjectType.class);
    }

    @Test
    void parsesPutEndpointWithPathVarAndBody() {
        List<Endpoint> endpoints = parser.parseController(TestUserController.class);
        Endpoint put = endpoints.stream()
                .filter(e -> e.getMethodName().equals("updateUser"))
                .findFirst().orElseThrow();

        assertThat(put.getHttpMethod()).isEqualTo(HttpMethod.PUT);
        assertThat(put.getUrlArgs()).hasSize(1);
        assertThat(put.getBody()).isNotNull();
    }

    @Test
    void parsesDeleteWithVoidReturn() {
        List<Endpoint> endpoints = parser.parseController(TestUserController.class);
        Endpoint delete = endpoints.stream()
                .filter(e -> e.getMethodName().equals("deleteUser"))
                .findFirst().orElseThrow();

        assertThat(delete.getHttpMethod()).isEqualTo(HttpMethod.DELETE);
        assertThat(delete.getReturnType()).isEqualTo(PrimitiveType.Void);
    }

    @Test
    void parsesQueryParams() {
        List<Endpoint> endpoints = parser.parseController(TestUserController.class);
        Endpoint list = endpoints.stream()
                .filter(e -> e.getMethodName().equals("listUsers"))
                .findFirst().orElseThrow();

        assertThat(list.getParams()).hasSize(2);
        assertThat(list.getParams().get(0).name()).isEqualTo("filter");
        assertThat(list.getParams().get(0).required()).isTrue();
        assertThat(list.getParams().get(1).name()).isEqualTo("sort");
        assertThat(list.getParams().get(1).required()).isFalse();
    }

    @Test
    void parsesMultiplePaths() {
        List<Endpoint> endpoints = parser.parseController(TestUserController.class);
        List<Endpoint> activeEndpoints = endpoints.stream()
                .filter(e -> e.getMethodName().equals("getActiveUsers"))
                .toList();

        assertThat(activeEndpoints).hasSize(2);
        assertThat(activeEndpoints.get(0).getUrl()).isEqualTo("/api/users/active");
        assertThat(activeEndpoints.get(1).getUrl()).isEqualTo("/api/users/enabled");
    }

    @Test
    void unwrapsMono() {
        List<Endpoint> endpoints = parser.parseController(TestUserController.class);
        Endpoint patch = endpoints.stream()
                .filter(e -> e.getMethodName().equals("patchUser"))
                .findFirst().orElseThrow();

        assertThat(patch.getReturnType()).isInstanceOf(ObjectType.class);
        assertThat(((ObjectType) patch.getReturnType()).getName()).isEqualTo("SimpleDto");
    }

    @Test
    void unwrapsFluxToArray() {
        List<Endpoint> endpoints = parser.parseController(TestUserController.class);
        Endpoint stream = endpoints.stream()
                .filter(e -> e.getMethodName().equals("streamUsers"))
                .findFirst().orElseThrow();

        assertThat(stream.getReturnType()).isInstanceOf(ArrayType.class);
    }

    @Test
    void handlesRawResponseEntity() {
        List<Endpoint> endpoints = parser.parseController(TestUserController.class);
        Endpoint raw = endpoints.stream()
                .filter(e -> e.getMethodName().equals("rawEntity"))
                .findFirst().orElseThrow();

        assertThat(raw.getReturnType()).isEqualTo(PrimitiveType.Void);
    }

    // --- File upload ---

    @Test
    void parsesRequestPartAsFileParam() {
        List<Endpoint> endpoints = parser.parseController(TestFileUploadController.class);
        Endpoint upload = endpoints.stream()
                .filter(e -> e.getMethodName().equals("upload"))
                .findFirst().orElseThrow();

        assertThat(upload.getFileParams()).hasSize(1);
        assertThat(upload.getFileParams().get(0).name()).isEqualTo("file");
        assertThat(upload.getParams()).hasSize(1);
        assertThat(upload.getParams().get(0).name()).isEqualTo("description");
    }

    @Test
    void parsesMultipartFileParamAsFileParam() {
        List<Endpoint> endpoints = parser.parseController(TestFileUploadController.class);
        Endpoint multi = endpoints.stream()
                .filter(e -> e.getMethodName().equals("multiUpload"))
                .findFirst().orElseThrow();

        assertThat(multi.getFileParams()).hasSize(1);
        assertThat(multi.getFileParams().get(0).name()).isEqualTo("avatar");
        assertThat(multi.getParams()).hasSize(1);
        assertThat(multi.getParams().get(0).name()).isEqualTo("name");
    }

    // --- Pagination ---

    @Test
    void parsesPagedEndpoint() {
        List<Endpoint> endpoints = parser.parseController(TestPaginatedController.class);
        Endpoint ep = endpoints.get(0);

        assertThat(ep).isInstanceOf(PagedEndpoint.class);
        PagedEndpoint paged = (PagedEndpoint) ep;
        assertThat(paged.getPageVariable()).isEqualTo("page");
        assertThat(paged.getPageSizeVariable()).isEqualTo("size");
        // page and size should NOT be in regular params
        assertThat(paged.getParams()).hasSize(1);
        assertThat(paged.getParams().get(0).name()).isEqualTo("filter");
    }
}
