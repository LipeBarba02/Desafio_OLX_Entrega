package dev.olx.simple.web;

import dev.olx.simple.application.AdService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ads")
public class AdController {
    private final AdService service;
    public AdController(AdService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<AdResponse> create(@Valid @RequestBody CreateAdRequest request) {
        AdResponse response = service.create(request);
        return ResponseEntity.created(URI.create("/ads/" + response.id())).body(response);
    }

    @GetMapping
    public Page<AdResponse> list(@RequestParam(required = false) String category,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        return service.list(category, page, size);
    }

    @GetMapping("/{id}")
    public AdResponse get(@PathVariable UUID id) { return service.get(id); }
}
