package dev.olx.ads.persistence;

import dev.olx.ads.domain.AdStatus;
import dev.olx.ads.domain.Category;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdRepository extends JpaRepository<AdEntity, UUID> {
    Page<AdEntity> findByStatusAndCategory(AdStatus status, Category category, Pageable pageable);
    Page<AdEntity> findByStatus(AdStatus status, Pageable pageable);
}
