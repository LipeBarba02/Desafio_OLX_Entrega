package dev.olx.ads.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Spring Data derives basic CRUD from JpaRepository; the workflow updates below are explicit JPQL. */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    /** PENDING events are due; IN_FLIGHT events are abandoned leases eligible for recovery. */
    @Transactional(readOnly = true)
    @Query("""
        select event
        from OutboxEventEntity event
        where (event.status = 'PENDING' and event.nextAttemptAt <= :now)
           or (event.status = 'IN_FLIGHT' and event.leaseUntil <= :now)
        order by event.createdAt asc
        """)
    List<OutboxEventEntity> findEligible(
        @Param("now") Instant now,
        Pageable pageable
    );

    /**
     * Conditional UPDATE is the claim arbitration across relay processes.
     * Exactly one process can move a currently due row into its lease window.
     * The returned count is 1 for the winner and 0 for a concurrent loser.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEventEntity event
        set event.status = 'IN_FLIGHT',
            event.leaseUntil = :leaseUntil,
            event.attemptCount = event.attemptCount + 1
        where event.id = :id
          and (
              (event.status = 'PENDING' and event.nextAttemptAt <= :now)
              or (event.status = 'IN_FLIGHT' and event.leaseUntil <= :now)
          )
        """)
    int claim(
        @Param("id") UUID id,
        @Param("now") Instant now,
        @Param("leaseUntil") Instant leaseUntil
    );

    /** Called only after the producer future completed successfully. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEventEntity event
        set event.status = 'PUBLISHED',
            event.publishedAt = :publishedAt,
            event.leaseUntil = null,
            event.lastError = null
        where event.id = :id
          and event.status = 'IN_FLIGHT'
        """)
    int markPublished(
        @Param("id") UUID id,
        @Param("publishedAt") Instant publishedAt
    );

    /** Releases the lease and delays another attempt after send failure or timeout. */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update OutboxEventEntity event
        set event.status = 'PENDING',
            event.nextAttemptAt = :nextAttemptAt,
            event.leaseUntil = null,
            event.lastError = :lastError
        where event.id = :id
          and event.status = 'IN_FLIGHT'
        """)
    int scheduleRetry(
        @Param("id") UUID id,
        @Param("nextAttemptAt") Instant nextAttemptAt,
        @Param("lastError") String lastError
    );
}
