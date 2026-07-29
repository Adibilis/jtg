package ch.adibilis.jtg.model.endpoints;

import ch.adibilis.jtg.model.types.Field;

/**
 * A {@code @RequestHeader}-bound method parameter.
 * <p>
 * {@code field} carries the TypeScript-facing parameter name (the Java parameter name),
 * its type and whether it's required — mirroring {@code @RequestHeader(required=...)}.
 * {@code headerName} is the wire header name resolved from the annotation's {@code value}/
 * {@code name} (falling back to the parameter name when neither is set), used for the actual
 * {@code HttpHeaders} key in generated code — it is intentionally decoupled from the parameter
 * name since callers commonly write {@code @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch}.
 */
public record HeaderParam(Field field, String headerName) {
}
