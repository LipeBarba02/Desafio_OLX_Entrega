package dev.olx.ads.web;

import dev.olx.ads.application.*;
import dev.olx.ads.domain.AdDraft;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/ads")
public class AdController {
    private final AdSubmissionService submission; private final AdQueryService query;
    public AdController(AdSubmissionService submission,AdQueryService query){this.submission=submission;this.query=query;}
    @PostMapping public ResponseEntity<AdCreatedResponse> create(@Valid @RequestBody CreateAdRequest request){
        AdDraft draft=request.toDraft();AdSubmissionResult result=submission.submit(draft);
        return ResponseEntity.accepted().location(URI.create("/ads/"+result.adId())).body(new AdCreatedResponse(result.adId(),result.status()));
    }
    @GetMapping
    public Page<AdResponse> list(
        @RequestParam(name = "category", required = false) String category,
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return query.list(category, page, size);
    }

    @GetMapping("/{id}")
    public AdResponse get(@PathVariable("id") UUID id) {
        return query.get(id);
    }
}
