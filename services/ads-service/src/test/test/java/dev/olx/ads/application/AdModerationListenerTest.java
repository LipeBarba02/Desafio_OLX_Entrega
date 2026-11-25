package dev.olx.ads.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.olx.ads.domain.AdStatus;
import dev.olx.ads.persistence.AdEntity;
import dev.olx.ads.persistence.AdRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AdModerationListenerTest {
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test void appliesApprovedDecisionToExistingAd() throws Exception {
        UUID adId = UUID.randomUUID();
        AdRepository repository = mock(AdRepository.class);
        AdEntity ad = new AdEntity(adId, draft(), Instant.parse("2026-11-24T10:00:00Z"));
        when(repository.findById(adId)).thenReturn(Optional.of(ad));
        var listener = new AdModerationListener(json, repository);

        listener.onDecision(event(adId, "approved", null));

        assertEquals(AdStatus.APPROVED, ad.getStatus());
        assertNull(ad.getRejectionReason());
    }

    @Test void appliesRejectedDecisionAndReason() throws Exception {
        UUID adId = UUID.randomUUID();
        AdRepository repository = mock(AdRepository.class);
        AdEntity ad = new AdEntity(adId, draft(), Instant.now());
        when(repository.findById(adId)).thenReturn(Optional.of(ad));

        new AdModerationListener(json, repository).onDecision(event(adId, "rejected", "policy"));

        assertEquals(AdStatus.REJECTED, ad.getStatus());
        assertEquals("policy", ad.getRejectionReason());
    }

    @Test void acknowledgesStaleDecisionForMissingAd() throws Exception {
        AdRepository repository = mock(AdRepository.class);
        when(repository.findById(any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> new AdModerationListener(json, repository)
            .onDecision(event(UUID.randomUUID(), "approved", null)));
    }

    @Test void rejectsUnknownDecisionStatusSoKafkaCanRetryThenDlt() throws Exception {
        UUID adId = UUID.randomUUID();
        AdRepository repository = mock(AdRepository.class);
        when(repository.findById(adId)).thenReturn(Optional.of(new AdEntity(adId, draft(), Instant.now())));

        assertThrows(IllegalArgumentException.class, () -> new AdModerationListener(json, repository)
            .onDecision(event(adId, "pending", null)));
    }

    private String event(UUID id, String status, String reason) throws Exception {
        return json.writeValueAsString(new AdModerationDecidedV1(UUID.randomUUID(), 1, Instant.now(), id,
            status, reason, Instant.now(), "Notebook usado em bom estado", "Descrição detalhada",
            "eletronicos", new BigDecimal("1200.00")));
    }

    private static dev.olx.ads.domain.AdDraft draft() {
        return new dev.olx.ads.domain.AdDraft("Notebook usado em bom estado", "Descrição detalhada",
            new BigDecimal("1200.00"), dev.olx.ads.domain.Category.fromCode("eletronicos"),
            List.of("https://example.com/a.jpg"));
    }
}
