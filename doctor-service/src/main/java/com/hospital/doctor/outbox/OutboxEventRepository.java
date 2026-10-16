package com.hospital.doctor.outbox;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * SELECT ... FOR UPDATE SKIP LOCKED (lock timeout -2): several relay instances can
     * run at the same time without publishing the same row twice or blocking each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select o from OutboxEvent o where o.publishedAt is null order by o.id")
    List<OutboxEvent> lockNextBatch(Pageable pageable);

    long countByPublishedAtIsNull();

    List<OutboxEvent> findByTopicOrderByIdAsc(String topic);

    @Modifying
    @Query("delete from OutboxEvent o where o.publishedAt < :before")
    int deletePublishedBefore(@Param("before") Instant before);
}
