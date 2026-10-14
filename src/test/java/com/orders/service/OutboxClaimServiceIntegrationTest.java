package com.orders.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.orders.inventory.outbox.service.impl.OutboxClaimServiceImpl;
import com.orders.inventory.outbox.service.OutboxClaimService;
import com.orders.inventory.outbox.repository.OutboxEventRepository;
import com.orders.inventory.outbox.domain.model.OutboxEvent;
import com.orders.inventory.outbox.domain.model.OutboxStatus;
import com.orders.integeration.AbstractInventoryIntegrationTest;

@Import(OutboxClaimServiceImpl.class)
public class OutboxClaimServiceIntegrationTest extends AbstractInventoryIntegrationTest {

        @Autowired
        private JdbcTemplate jdbcTemplate;

        @Autowired
        private OutboxEventRepository repository;

        @Autowired
        private OutboxClaimService claimService;

        @Autowired
        private EntityManager entityManager;

        @Test
        void shouldConnectToPostgreSQLContainer() {
                String databaseName = jdbcTemplate.queryForObject(
                                "SELECT current_database()", String.class);

                assertThat(databaseName).isEqualTo("inventory_test");
        }

        @Test
        @Transactional
        void shouldReclaimStaleProcessingEvent() {

                // Given: an event stuck in PROCESSING beyond the timeout
                Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
                Instant processingTimeout = now.minusSeconds(300);
                Instant staleProcessingAt = now.minusSeconds(600);

                String oldInstance = "inventory-instance-old";
                String newInstance = "inventory-instance-new";

                OutboxEvent event = OutboxEvent.builder()
                                .eventId(UUID.randomUUID())
                                .eventType("InventoryReserved")
                                .aggregateType("InventoryReservation")
                                .topic("inventory.reserved")
                                .eventKey(UUID.randomUUID().toString())
                                .aggregateId(UUID.randomUUID())
                                .payload("{\"eventId\":\"test-event\"}")
                                .status(OutboxStatus.PROCESSING)
                                .retryCount(0)
                                .maxRetries(10)
                                .nextRetryAt(now)
                                .processingAt(staleProcessingAt)
                                .processingInstanceId(oldInstance)
                                .createdAt(now.minusSeconds(900))
                                .updatedAt(staleProcessingAt)
                                .build();

                OutboxEvent saved = repository.saveAndFlush(event);
                UUID eventId = saved.getId();

                // When: a new instance claims eligible events
                List<OutboxEvent> claimed = claimService.claim(
                                now,
                                processingTimeout,
                                50,
                                newInstance);

                // Then: the stale event is reclaimed
                assertThat(claimed).extracting(OutboxEvent::getId).contains(eventId);

                OutboxEvent reclaimed = claimed.stream()
                                .filter(candidate -> candidate.getId().equals(eventId))
                                .findFirst()
                                .orElseThrow();

                assertThat(reclaimed.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
                assertThat(reclaimed.getProcessingInstanceId()).isEqualTo(newInstance);
                assertThat(reclaimed.getProcessingAt()).isEqualTo(now);

                // Clear the persistence context to verify the database state,
                // rather than relying on the in-memory entity.
                entityManager.flush();
                entityManager.clear();

                OutboxEvent persisted = repository.findById(eventId).orElseThrow();
                assertThat(persisted.getStatus()).isEqualTo(OutboxStatus.PROCESSING);
                assertThat(persisted.getProcessingInstanceId()).isEqualTo(newInstance);
                assertThat(persisted.getProcessingAt()).isEqualTo(now);
        }

        @Test
        void shouldAllowOnlyOneInstanceToClaimSameEventConcurrently()
                        throws Exception {

                // Given: one eligible PENDING event
                Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);

                OutboxEvent event = OutboxEvent.builder()
                                .eventId(UUID.randomUUID())
                                .eventType("InventoryReserved")
                                .aggregateType("InventoryReservation")
                                .topic("inventory.reserved")
                                .eventKey(UUID.randomUUID().toString())
                                .aggregateId(UUID.randomUUID())
                                .payload("{\"eventId\":\"concurrent-test\"}")
                                .status(OutboxStatus.PENDING)
                                .retryCount(0)
                                .maxRetries(10)
                                .nextRetryAt(now)
                                .processingAt(null)
                                .processingInstanceId(null)
                                .createdAt(now.minusSeconds(60))
                                .updatedAt(now)
                                .build();

                UUID eventId = repository.saveAndFlush(event).getId();

                String instanceA = "inventory-instance-A";
                String instanceB = "inventory-instance-B";

                Instant processingTimeout = now.minusSeconds(300);

                ExecutorService executor = Executors.newFixedThreadPool(2);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch start = new CountDownLatch(1);

                try {
                        java.util.concurrent.Callable<List<OutboxEvent>> workerA = () -> {
                                ready.countDown();

                                if (!start.await(10, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                        "Timed out waiting to start worker A");
                                }

                                return claimService.claim(
                                                now, processingTimeout, 50, instanceA);
                        };

                        java.util.concurrent.Callable<List<OutboxEvent>> workerB = () -> {
                                ready.countDown();

                                if (!start.await(10, TimeUnit.SECONDS)) {
                                        throw new IllegalStateException(
                                                        "Timed out waiting to start worker B");
                                }

                                return claimService.claim(
                                                now, processingTimeout, 50, instanceB);
                        };

                        Future<List<OutboxEvent>> futureA = executor.submit(workerA);
                        Future<List<OutboxEvent>> futureB = executor.submit(workerB);

                        assertThat(ready.await(10, TimeUnit.SECONDS))
                                        .as("Both workers should be ready before claiming")
                                        .isTrue();

                        // When: both workers attempt to claim the same event
                        start.countDown();

                        List<OutboxEvent> claimedA = futureA.get(30, TimeUnit.SECONDS);

                        List<OutboxEvent> claimedB = futureB.get(30, TimeUnit.SECONDS);

                        // Then: exactly one worker claims the event
                        Set<UUID> idsA = claimedA.stream()
                                        .map(OutboxEvent::getId)
                                        .collect(java.util.stream.Collectors.toSet());

                        Set<UUID> idsB = claimedB.stream()
                                        .map(OutboxEvent::getId)
                                        .collect(java.util.stream.Collectors.toSet());

                        Set<UUID> allClaimedIds = new HashSet<>(idsA);
                        allClaimedIds.addAll(idsB);

                        assertThat(idsA)
                                        .doesNotContainAnyElementsOf(idsB);

                        assertThat(allClaimedIds)
                                        .containsExactly(eventId);

                        assertThat(claimedA.size() + claimedB.size())
                                        .isEqualTo(1);

                        // Verify the committed database state.
                        entityManager.clear();

                        OutboxEvent persisted = repository.findById(eventId).orElseThrow();

                        assertThat(persisted.getStatus())
                                        .isEqualTo(OutboxStatus.PROCESSING);

                        assertThat(persisted.getProcessingInstanceId())
                                        .isIn(instanceA, instanceB);

                        assertThat(persisted.getProcessingAt())
                                        .isEqualTo(now);

                } finally {
                        executor.shutdownNow();
                }
        }

}
