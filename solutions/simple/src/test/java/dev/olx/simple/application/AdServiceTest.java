package dev.olx.simple.application;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import dev.olx.simple.moderation.ModerationClient;
import dev.olx.simple.persistence.AdRepository;
import dev.olx.simple.web.CreateAdRequest;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;

class AdServiceTest {
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
}
