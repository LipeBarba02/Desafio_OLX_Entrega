package dev.olx.ads.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.olx.ads.domain.AdDraft;
import dev.olx.ads.persistence.AdEntity;
import dev.olx.ads.persistence.AdRepository;
import dev.olx.ads.persistence.OutboxEventEntity;
import dev.olx.ads.persistence.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application use case: accept a valid draft and durably schedule its asynchronous moderation. */
@Service
public class AdSubmissionService {
    private final AdRepository adRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdSubmissionService(
        AdRepository adRepository,
        OutboxEventRepository outboxRepository,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.adRepository = adRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * The transaction boundary is PostgreSQL only. Both rows commit, or neither commits.
     * Kafka and the external moderation HTTP call are deliberately outside this method.
     */
    @Transactional
    public AdSubmissionResult submit(AdDraft draft) {
        Instant now = clock.instant();
        UUID adId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        AdEntity ad = new AdEntity(adId, draft, now);
        adRepository.save(ad);

        AdSubmittedV1 event = new AdSubmittedV1(
            eventId,
            1,
            now,
            adId,
            draft.title(),
            draft.description(),
            draft.price(),
            draft.category().code()
        );

        OutboxEventEntity outboxEvent = OutboxEventEntity.pending(
            eventId,
            adId,
            "AdSubmitted",
            1,
            serialize(event),
            now
        );
        outboxRepository.save(outboxEvent);

        return new AdSubmissionResult(adId, ad.getStatus());
    }

    private String serialize(AdSubmittedV1 event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            // Throwing inside the method marks the PostgreSQL transaction for rollback.
            throw new IllegalStateException("Could not serialize AdSubmittedV1", exception);
        }
    }
}
