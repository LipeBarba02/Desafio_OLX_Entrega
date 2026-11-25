package dev.olx.simple.persistence;

import dev.olx.simple.domain.Category;
import dev.olx.simple.domain.ModerationStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdRepository extends JpaRepository<AdEntity, UUID> {
    Page<AdEntity> findByStatusAndCategory(ModerationStatus status, Category category, Pageable pageable);
    Page<AdEntity> findByStatus(ModerationStatus status, Pageable pageable);
}
