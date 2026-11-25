package dev.olx.ads.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.olx.ads.domain.AdStatus;
import dev.olx.ads.domain.Category;
import dev.olx.ads.persistence.AdEntity;
import dev.olx.ads.persistence.AdRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

class AdQueryServiceTest {
    @Test void listFiltersApprovedAdsAndBoundsPageSize() {
        AdRepository repository = mock(AdRepository.class);
        when(repository.findByStatusAndCategory(eq(AdStatus.APPROVED), eq(Category.fromCode("eletronicos")), any()))
            .thenReturn(Page.empty());
        AdQueryService service = new AdQueryService(repository);

        service.list(" eletronicos ", -4, 500);

        var pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByStatusAndCategory(eq(AdStatus.APPROVED), eq(Category.fromCode("eletronicos")), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(100, pageable.getValue().getPageSize());
        assertEquals("createdAt: DESC", pageable.getValue().getSort().toString());
    }

    @Test void getThrowsNotFoundWhenIdDoesNotExist() {
        AdRepository repository = mock(AdRepository.class);
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(AdNotFoundException.class, () -> new AdQueryService(repository).get(id));
    }
}
