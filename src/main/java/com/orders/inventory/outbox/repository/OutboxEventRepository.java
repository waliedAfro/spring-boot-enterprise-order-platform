package com.orders.inventory.outbox.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.orders.inventory.outbox.domain.model.OutboxEvent;
import com.orders.inventory.outbox.domain.model.OutboxStatus;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

        @Query("""
                        SELECT e
                        FROM OutboxEvent e
                        WHERE e.status = :status
                          AND e.nextRetryAt <= :now
                        ORDER BY e.createdAt ASC
                        """)
        List<OutboxEvent> findEventsForRetry(
                        @Param("status") OutboxStatus status,
                        @Param("now") Instant now);

        @Query("""
                        SELECT e
                        FROM OutboxEvent e
                        WHERE
                            (
                                e.status = com.orders.inventory.outbox.domain.model.OutboxStatus.PENDING
                                AND e.nextRetryAt <= :now
                            )
                            OR
                            (
                                e.status = com.orders.inventory.outbox.domain.model.OutboxStatus.PROCESSING
                                AND e.processingAt < :processingTimeout
                            )
                        ORDER BY e.createdAt ASC
                        """)
        List<OutboxEvent> findEventsForClaim(
                        @Param("now") Instant now,
                        @Param("processingTimeout") Instant processingTimeout,
                        org.springframework.data.domain.Pageable pageable);

        @Modifying
        @Query("""
                        UPDATE OutboxEvent e
                        SET
                            e.status = 'PROCESSING',
                            e.processingInstanceId = :instanceId,
                            e.processingAt = :now
                        WHERE e.id = :id
                          AND (
                                e.status = 'PENDING'
                                OR (
                                    e.status = 'PROCESSING'
                                    AND e.processingAt < :processingTimeout
                                )
                          )
                        """)
        int markProcessing(
                        @Param("id") UUID id,
                        @Param("instanceId") String instanceId,
                        @Param("now") Instant now,
                        @Param("processingTimeout") Instant processingTimeout);

        @Modifying
        @Query("""
                        UPDATE OutboxEvent e
                        SET
                            e.status = 'PUBLISHED',
                            e.publishedAt = :publishedAt,
                            e.processingInstanceId = null,
                            e.processingAt = null
                        WHERE e.id = :id
                          AND e.processingInstanceId = :instanceId
                        """)
        int markPublished(
                        @Param("id") UUID id,
                        @Param("instanceId") String instanceId,
                        @Param("publishedAt") Instant publishedAt);

        @Modifying
        @Query("""
                        UPDATE OutboxEvent e
                        SET
                            e.status = 'PENDING',
                            e.retryCount = e.retryCount + 1,
                            e.nextRetryAt = :nextRetryAt,
                            e.processingInstanceId = null,
                            e.processingAt = null,
                            e.lastError = :lastError
                        WHERE e.id = :id
                          AND e.processingInstanceId = :instanceId
                        """)
        int markFailed(
                        @Param("id") UUID id,
                        @Param("instanceId") String instanceId,
                        @Param("nextRetryAt") Instant nextRetryAt,
                        @Param("lastError") String lastError);

        @Modifying
        @Query("""
                        UPDATE OutboxEvent e
                        SET
                            e.status = 'FAILED',
                            e.processingInstanceId = null,
                            e.processingAt = null,
                            e.lastError = :lastError
                        WHERE e.id = :id
                          AND e.processingInstanceId = :instanceId
                        """)
        int markPermanentlyFailed(
                        @Param("id") UUID id,
                        @Param("instanceId") String instanceId,
                        @Param("lastError") String lastError);
}
