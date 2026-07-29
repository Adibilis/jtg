package ch.adibilis.jtg.parser.fixtures;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Fixture mirroring the base repo's If-Match/ETag replace-endpoint pattern:
 * {@code @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch} on a {@code ResponseEntity<T>}-returning
 * PUT handler, plus an optional header and a header combined with a query param.
 */
@RestController
@RequestMapping("/api/replace-demo")
public class TestReplaceController {

    @PutMapping("/{id}")
    public ResponseEntity<SimpleDto> replace(
            @PathVariable long id,
            @RequestBody SimpleDto body,
            @RequestHeader(HttpHeaders.IF_MATCH) String ifMatch) {
        return null;
    }

    @GetMapping("/{id}")
    public SimpleDto getWithOptionalHeader(
            @PathVariable long id,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {
        return null;
    }

    @GetMapping
    public SimpleDto search(
            @RequestParam String filter,
            @RequestHeader("X-Api-Version") String apiVersion) {
        return null;
    }
}
