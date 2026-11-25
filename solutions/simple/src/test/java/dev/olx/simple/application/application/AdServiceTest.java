package dev.olx.simple.application.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.olx.simple.application.AdService;
import dev.olx.simple.moderation.ModerationClient;
import dev.olx.simple.persistence.AdRepository;
import dev.olx.simple.web.CreateAdRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import dev.olx.simple.domain.Category;
import dev.olx.simple.domain.ModerationStatus;
import dev.olx.simple.persistence.AdEntity;
import org.mockito.InOrder;
import org.junit.jupiter.api.Test;

class AdServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-24T12:00:00Z");

    @Test void createsAndPersistsApprovedAdOnlyAfterModeration() {
        ModerationClient moderation = mock(ModerationClient.class);
        AdRepository repository = mock(AdRepository.class);
        var service = new AdService(repository, moderation, Clock.fixed(NOW, ZoneOffset.UTC));
        var request = validRequest();
        when(moderation.check("Notebook usado em bom estado", "Descrição detalhada",
            new BigDecimal("1800.00"), "eletronicos"))
            .thenReturn(new ModerationClient.Decision("approved", null));
        when(repository.save(any(AdEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(request);

        assertEquals(ModerationStatus.APPROVED, response.status());
        assertEquals("eletronicos", response.category());
        assertEquals(List.of("https://example.com/a.jpg"), response.images());
        assertEquals(NOW, response.createdAt());
        InOrder order = inOrder(moderation, repository);
        order.verify(moderation).check("Notebook usado em bom estado", "Descrição detalhada",
            new BigDecimal("1800.00"), "eletronicos");
        order.verify(repository).save(any(AdEntity.class));
    }

    @Test void persistsRejectedDecisionAndReason() {
        ModerationClient moderation = mock(ModerationClient.class);
        AdRepository repository = mock(AdRepository.class);
        var service = new AdService(repository, moderation, Clock.fixed(NOW, ZoneOffset.UTC));
        when(moderation.check(any(), any(), any(), any()))
            .thenReturn(new ModerationClient.Decision("rejected", "prohibited item"));
        when(repository.save(any(AdEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(validRequest());

        assertEquals(ModerationStatus.REJECTED, response.status());
        assertEquals("prohibited item", response.rejectionReason());
    }

    @Test void doesNotPersistWhenModerationFails() {
        ModerationClient moderation = mock(ModerationClient.class);
        AdRepository repository = mock(AdRepository.class);
        var service = new AdService(repository, moderation, Clock.systemUTC());
        when(moderation.check(any(), any(), any(), any())).thenThrow(new IllegalStateException("offline"));

        assertThrows(IllegalStateException.class, () -> service.create(validRequest()));
        verifyNoInteractions(repository);
    }

    @Test void rejectsUnknownModerationStatusWithoutPersisting() {
        ModerationClient moderation = mock(ModerationClient.class);
        AdRepository repository = mock(AdRepository.class);
        var service = new AdService(repository, moderation, Clock.systemUTC());
        when(moderation.check(any(), any(), any(), any()))
            .thenReturn(new ModerationClient.Decision("queued", null));

        assertThrows(IllegalStateException.class, () -> service.create(validRequest()));
        verifyNoInteractions(repository);
    }

    @Test void rejectsUnstorablePriceBeforeCallingModeration() {
        ModerationClient moderation = mock(ModerationClient.class);
        var service = new AdService(mock(AdRepository.class), moderation, Clock.systemUTC());
        var request = new CreateAdRequest(
            "Notebook usado em bom estado",
            "Descrição detalhada",
            new BigDecimal("1.001"),
            "eletronicos",
            List.of("https://example.com/a.jpg")
        );

        assertThrows(IllegalArgumentException.class, () -> service.create(request));
        verifyNoInteractions(moderation);
    }

    @Test void listOnlyReturnsApprovedAndNormalizesPageBounds() {
        AdRepository repository = mock(AdRepository.class);
        var service = new AdService(repository, mock(ModerationClient.class), Clock.systemUTC());
        when(repository.findByStatusAndCategory(org.mockito.ArgumentMatchers.eq(ModerationStatus.APPROVED),
            org.mockito.ArgumentMatchers.eq(Category.fromCode("eletronicos")), any()))
            .thenReturn(org.springframework.data.domain.Page.empty());

        service.list("eletronicos", -2, 500);

        var captor = org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        org.mockito.Mockito.verify(repository).findByStatusAndCategory(
            org.mockito.ArgumentMatchers.eq(ModerationStatus.APPROVED),
            org.mockito.ArgumentMatchers.eq(Category.fromCode("eletronicos")), captor.capture());
        assertEquals(0, captor.getValue().getPageNumber());
        assertEquals(100, captor.getValue().getPageSize());
    }

    private static CreateAdRequest validRequest() {
        return new CreateAdRequest("Notebook usado em bom estado", "Descrição detalhada",
            new BigDecimal("1800.00"), "eletronicos", List.of(" https://example.com/a.jpg "));
    }
}
