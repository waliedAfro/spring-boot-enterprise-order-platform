package com.orders.kafka.producer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.orders.inventory.kafka.producer.KafkaInventoryProcessor;
import com.orders.inventory.outbox.domain.model.OutboxEvent;
import com.orders.inventory.outbox.domain.model.OutboxStatus;
import com.orders.inventory.outbox.repository.OutboxEventRepository;
import com.orders.inventory.outbox.service.OutboxClaimService;
import com.orders.inventory.outbox.service.impl.OutboxInstanceId;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.support.SendResult;

public class KafkaInventoryProcessorTest {

        private KafkaTemplate<String, String> kafkaTemplate;
        private OutboxClaimService claimService;
        private OutboxEventRepository repository;
        private OutboxInstanceId instanceId;
        private KafkaInventoryProcessor processor;

        private final String instance = "inventory-test-instance";
        private final String reservedTopic = "inventory.reserved";
        private final String failedTopic = "inventory.reservation-failed";

        @SuppressWarnings("unchecked")
        @BeforeEach
        void setUp() {
                kafkaTemplate = mock(KafkaTemplate.class);
                claimService = mock(OutboxClaimService.class);
                repository = mock(OutboxEventRepository.class);
                instanceId = mock(OutboxInstanceId.class);

                when(instanceId.get()).thenReturn(instance);

                processor = new KafkaInventoryProcessor(kafkaTemplate, claimService, repository, instanceId);

                ReflectionTestUtils.setField(processor, "inventoryReservedTopic", reservedTopic);
                ReflectionTestUtils.setField(processor, "inventoryReservationFailedTopic", failedTopic);
                ReflectionTestUtils.setField(processor, "batchSize", 50);
                ReflectionTestUtils.setField(processor, "processingTimeoutSeconds", 300L);
                ReflectionTestUtils.setField(processor, "maxRetries", 10);
                ReflectionTestUtils.setField(processor, "maxRetryDelaySeconds", 300L);
        }

        @Test
        void shouldScheduleRetryWhenKafkaPublishingFails() {

                OutboxEvent event = newEvent("InventoryReserved", 0, 10);

                when(claimService.claim(
                                any(Instant.class),
                                any(Instant.class),
                                eq(50),
                                eq(instance)))
                                .thenReturn(List.of(event));

                when(kafkaTemplate.send(
                                eq(reservedTopic),
                                eq(event.getAggregateId().toString()),
                                eq(event.getPayload())))
                                .thenReturn(CompletableFuture.failedFuture(
                                                new RuntimeException("Kafka broker unavailable")));

                Instant before = Instant.now();

                processor.proccessEvent();

                ArgumentCaptor<Instant> retryTime = ArgumentCaptor.forClass(Instant.class);

                verify(repository).markFailed(
                                eq(event.getId()),
                                eq(instance),
                                retryTime.capture(),
                                eq("java.lang.RuntimeException: Kafka broker unavailable"));

                verify(repository, never()).markPublished(
                                any(UUID.class),
                                anyString(),
                                any(Instant.class));

                verify(repository, never()).markPermanentlyFailed(
                                any(UUID.class),
                                anyString(),
                                anyString());

                assertThat(retryTime.getValue())
                                .isAfterOrEqualTo(before.plusSeconds(5))
                                .isBeforeOrEqualTo(before.plusSeconds(10));

        }

        @Test
        void shouldMarkEventPermanentlyFailedWhenRetryLimitIsReached() {

                OutboxEvent event = newEvent("InventoryReserved", 10, 10);

                when(claimService.claim(
                                any(Instant.class),
                                any(Instant.class),
                                eq(50),
                                eq(instance)))
                                .thenReturn(List.of(event));

                when(kafkaTemplate.send(
                                eq(reservedTopic),
                                eq(event.getAggregateId().toString()),
                                eq(event.getPayload())))
                                .thenReturn(CompletableFuture.failedFuture(
                                                new RuntimeException("Kafka broker unavailable")));

                processor.proccessEvent();

                verify(repository).markPermanentlyFailed(
                                eq(event.getId()),
                                eq(instance),
                                eq("java.lang.RuntimeException: Kafka broker unavailable"));

                verify(repository, never()).markFailed(
                                any(UUID.class),
                                anyString(),
                                any(Instant.class),
                                anyString());

                verify(repository, never()).markPublished(
                                any(UUID.class),
                                anyString(),
                                any(Instant.class));
        }

        private OutboxEvent newEvent(String eventType, int retryCount, int maxRetries) {

                Instant now = Instant.now();

                return OutboxEvent.builder()
                                .id(UUID.randomUUID())
                                .eventId(UUID.randomUUID())
                                .eventType(eventType)
                                .aggregateType("InventoryReservation")
                                .topic(reservedTopic)
                                .eventKey(UUID.randomUUID().toString())
                                .aggregateId(UUID.randomUUID())
                                .payload("{\"eventId\":\"test-event\"}")
                                .status(OutboxStatus.PROCESSING)
                                .retryCount(retryCount)
                                .maxRetries(maxRetries)
                                .nextRetryAt(now)
                                .processingAt(now)
                                .processingInstanceId(instance)
                                .createdAt(now)
                                .updatedAt(now)
                                .build();
        }



@Test
@SuppressWarnings("unchecked")
void shouldRepublishStaleProcessingEventAfterTimeout() {

    // Given: an event stuck in PROCESSING for 10 minutes
    Instant staleProcessingTime = Instant.now().minusSeconds(600);

    OutboxEvent event = newEvent("InventoryReserved", 0, 10);
    event.setProcessingAt(staleProcessingTime);

    when(claimService.claim(
            any(Instant.class),
            any(Instant.class),
            eq(50),
            eq(instance)))
            .thenReturn(List.of(event));

    // And: Kafka successfully publishes the recovered event
    SendResult<String, String> sendResult = mock(SendResult.class);
    RecordMetadata metadata = mock(RecordMetadata.class);

    when(sendResult.getRecordMetadata()).thenReturn(metadata);
    when(metadata.partition()).thenReturn(0);
    when(metadata.offset()).thenReturn(123L);

    when(kafkaTemplate.send(
            eq(reservedTopic),
            eq(event.getAggregateId().toString()),
            eq(event.getPayload())))
            .thenReturn(CompletableFuture.completedFuture(sendResult));

    // When: the processor runs its recovery cycle
    processor.proccessEvent();

    // Then: claim uses a timeout later than the stale processing timestamp
    ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> timeoutCaptor = ArgumentCaptor.forClass(Instant.class);

    verify(claimService).claim(
            nowCaptor.capture(),
            timeoutCaptor.capture(),
            eq(50),
            eq(instance));

    assertThat(timeoutCaptor.getValue())
            .isAfter(staleProcessingTime);

    assertThat(nowCaptor.getValue())
            .isAfterOrEqualTo(timeoutCaptor.getValue());

    // And: the event is republished and marked as published
    verify(kafkaTemplate).send(
            eq(reservedTopic),
            eq(event.getAggregateId().toString()),
            eq(event.getPayload()));

    verify(repository).markPublished(
            eq(event.getId()),
            eq(instance),
            any(Instant.class));

    verify(repository, never()).markFailed(
            any(UUID.class),
            anyString(),
            any(Instant.class),
            anyString());

    verify(repository, never()).markPermanentlyFailed(
            any(UUID.class),
            anyString(),
            anyString());
}

}
