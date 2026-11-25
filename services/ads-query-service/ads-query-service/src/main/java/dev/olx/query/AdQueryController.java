package dev.olx.query;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/ads")
public class AdQueryController {
    private static final int MAX_PAGE_SIZE=100;
    private final AdReadService reads;
    public AdQueryController(AdReadService reads){this.reads=reads;}
    @GetMapping("/search")
    public SearchPage search(
        @RequestParam(name = "q", defaultValue = "") String q,
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "size", defaultValue = "20") int size,
        @RequestParam(name = "cursor", required = false) String cursor
    ) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be between 1 and 100");
        }
        return reads.search(q, category, size, cursor);
    }

    @GetMapping("/{id}")
    public AdDetailResponse detail(@PathVariable("id") UUID id) {
        AdDetailResponse found = reads.detail(id);
        if (found == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return found;
    }
}
