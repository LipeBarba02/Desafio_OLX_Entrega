package dev.olx.simple.application;

import dev.olx.simple.domain.Category;
import dev.olx.simple.domain.ModerationStatus;
import dev.olx.simple.moderation.ModerationClient;
import dev.olx.simple.persistence.AdEntity;
import dev.olx.simple.persistence.AdRepository;
import dev.olx.simple.web.CreateAdRequest;
import dev.olx.simple.web.AdResponse;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdService {
    private static final int MAX_PAGE_SIZE = 100;
    private final AdRepository repository;
    private final ModerationClient moderation;
    private final Clock clock;

    public AdService(AdRepository repository, ModerationClient moderation, Clock clock) {
        this.repository = repository; this.moderation = moderation; this.clock = clock;
    }

    /** Calls the external API before starting the short database transaction. */
    public AdResponse create(CreateAdRequest request) {
        Category category = request.parsedCategory();
        requireDatabasePrice(request.price());
        var images = request.normalizedImages(); // reject malformed image URLs before calling the external service.
        var decision = moderation.check(request.title().strip(), request.description().strip(), request.price(), category.code());
        ModerationStatus status = switch (decision.status().toLowerCase()) {
            case "approved" -> ModerationStatus.APPROVED;
            case "rejected" -> ModerationStatus.REJECTED;
            default -> throw new IllegalStateException("Unsupported moderation status: " + decision.status());
        };
        AdEntity saved = save(request, category, images, status, decision.rejectionReason());
        return AdResponse.from(saved);
    }

    private void requireDatabasePrice(java.math.BigDecimal price) {
        var normalized = price.stripTrailingZeros();
        if (normalized.scale() > 2 || normalized.precision() - normalized.scale() > 17) {
            throw new IllegalArgumentException("price must fit NUMERIC(19,2)");
        }
    }

    private AdEntity save(CreateAdRequest request, Category category, java.util.List<String> images, ModerationStatus status, String reason) {
        AdEntity ad = new AdEntity(UUID.randomUUID(), request.title().strip(), request.description().strip(),
            request.price(), category, images, status, reason, clock.instant());
        return repository.save(ad);
    }

    @Transactional(readOnly = true)
    public Page<AdResponse> list(String categoryCode, int page, int size) {
        int boundedSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(Math.max(page, 0), boundedSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AdEntity> ads = categoryCode == null || categoryCode.isBlank()
            ? repository.findByStatus(ModerationStatus.APPROVED, pageable)
            : repository.findByStatusAndCategory(ModerationStatus.APPROVED, Category.fromCode(categoryCode), pageable);
        return ads.map(AdResponse::from);
    }

    @Transactional(readOnly = true)
    public AdResponse get(UUID id) {
        return repository.findById(id).map(AdResponse::from).orElseThrow(() -> new AdNotFoundException(id));
    }
}
