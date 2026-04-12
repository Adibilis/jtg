package ch.adibilis.jtg.parser.fixtures;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class TestUserController {

    @GetMapping("/{id}")
    public SimpleDto getUser(@PathVariable String id) { return null; }

    @PostMapping
    public SimpleDto createUser(@RequestBody SimpleDto body) { return null; }

    @PutMapping("/{id}")
    public SimpleDto updateUser(@PathVariable String id, @RequestBody SimpleDto body) { return null; }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) { return null; }

    @GetMapping
    public List<SimpleDto> listUsers(
            @RequestParam String filter,
            @RequestParam(required = false) String sort) { return null; }

    @GetMapping({"/active", "/enabled"})
    public List<SimpleDto> getActiveUsers() { return null; }

    @PatchMapping("/{id}")
    public Mono<SimpleDto> patchUser(@PathVariable String id, @RequestBody SimpleDto body) { return null; }

    @GetMapping("/stream")
    public Flux<SimpleDto> streamUsers() { return null; }

    @GetMapping("/raw-entity")
    public ResponseEntity rawEntity() { return null; }
}
