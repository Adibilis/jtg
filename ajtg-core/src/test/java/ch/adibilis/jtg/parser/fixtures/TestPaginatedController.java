package ch.adibilis.jtg.parser.fixtures;

import ch.adibilis.jtg.pagination.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/items")
public class TestPaginatedController {

    @PagedQuery
    @GetMapping
    public List<SimpleDto> listItems(
            @PageParam @RequestParam int page,
            @PageSizeParam @RequestParam int size,
            @RequestParam(required = false) String filter) { return null; }
}
