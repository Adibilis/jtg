package ch.adibilis.jtg.model.endpoints;

import ch.adibilis.jtg.model.types.Field;
import ch.adibilis.jtg.model.types.Type;

import java.util.ArrayList;
import java.util.List;

public class Endpoint {

    private String className;
    private String methodName;
    private HttpMethod httpMethod;
    private String url;
    private Type returnType;
    private List<Field> urlArgs = new ArrayList<>();
    private Type body;
    private List<Field> params = new ArrayList<>();
    private List<Field> fileParams = new ArrayList<>();
    private List<HeaderParam> headers = new ArrayList<>();
    /**
     * True when the Java method's declared return type is a parameterized
     * {@code ResponseEntity<T>} (not a raw, un-parameterized {@code ResponseEntity}).
     * {@code returnType} above already holds the unwrapped {@code T} either way; this flag
     * additionally tells endpoint writers whether it's safe to emit a "…WithResponse" variant
     * returning {@code Observable<HttpResponse<T>>} for reading response headers (e.g. ETag).
     */
    private boolean responseEntity;

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public String getMethodName() { return methodName; }
    public void setMethodName(String methodName) { this.methodName = methodName; }
    public HttpMethod getHttpMethod() { return httpMethod; }
    public void setHttpMethod(HttpMethod httpMethod) { this.httpMethod = httpMethod; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Type getReturnType() { return returnType; }
    public void setReturnType(Type returnType) { this.returnType = returnType; }
    public List<Field> getUrlArgs() { return urlArgs; }
    public Type getBody() { return body; }
    public void setBody(Type body) { this.body = body; }
    public List<Field> getParams() { return params; }
    public List<Field> getFileParams() { return fileParams; }
    public List<HeaderParam> getHeaders() { return headers; }
    public boolean isResponseEntity() { return responseEntity; }
    public void setResponseEntity(boolean responseEntity) { this.responseEntity = responseEntity; }
}
