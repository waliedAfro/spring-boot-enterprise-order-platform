package com.orders.inventory.kafka.producer;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.orders.inventory.outbox.domain.model.OutboxEvent;
import com.orders.inventory.outbox.service.OutboxClaimService;
import com.orders.inventory.outbox.service.impl.OutboxInstanceId;
import com.orders.inventory.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaInventoryProcessor {

        private final KafkaTemplate<String, String> kafkaTemplate;
        private final OutboxClaimService claimService;
        private final OutboxEventRepository repository;
        private final OutboxInstanceId instanceId;

        @Value("${spring.kafka.topics.inventory-reserved}")
        private String inventoryReservedTopic;

        @Value("${spring.kafka.topics.inventory-reservation-failed}")
        private String inventoryReservationFailedTopic;

        @Value("${inventory.outbox.batch-size:50}")
        private int batchSize;

        @Value("${inventory.outbox.processing-timeout-seconds:300}")
        private long processingTimeoutSeconds;

        @Value("${inventory.outbox.max-retries:10}")
        private int maxRetries;

        @Value("${inventory.outbox.max-retry-delay-seconds:300}")
        private long maxRetryDelaySeconds;

        @Scheduled(fixedDelayString = "${inventory.outbox.scheduler.delay:1000}")
        public void proccessEvent() {

                Instant now = Instant.now();

                Instant processingTimeOut = now.minusSeconds(processingTimeoutSeconds);

                List<OutboxEvent> events = claimService.claim(
                                now,
                                processingTimeOut,
                                batchSize,
                                instanceId.get());

                for (OutboxEvent event : events) {
                        publishEvent(event);
                }
        }

        private void publishEvent(OutboxEvent event) {

                try {
                        String topic = resolveTopic(event);

                        Instant now = Instant.now();

                        SendResult<String, String> result = kafkaTemplate
                                        .send(
                                                        topic,
                                                        event.getAggregateId().toString(),
                                                        event.getPayload())
                                        .get();

                        log.info(
                                        "Published inventory outbox event: eventId={}, type={}, topic={}, partition={}, offset={}",
                                        event.getId(),
                                        event.getEventType(),
                                        topic,
                                        result.getRecordMetadata().partition(),
                                        result.getRecordMetadata().offset());

                        int updated = repository.markPublished(
                                        event.getId(),
                                        instanceId.get(),
                                        now);

                        if (updated == 0) {
                                log.warn(
                                                "Outbox event was not marked published; ownership may have changed: eventId={}",
                                                event.getId());
                        }

                } catch (Exception ex) {
                        handlePublishFailure(event, ex);
                }
        }

        private String resolveTopic(OutboxEvent event) {

                return switch (event.getEventType()) {

                        case "InventoryReserved" ->
                                inventoryReservedTopic;

                        case "InventoryReservationFailed" ->
                                inventoryReservationFailedTopic;

                        default ->
                                throw new IllegalArgumentException(
                                                "Unknown inventory outbox event type: "
                                                                + event.getEventType());
                };
        }

        private void handlePublishFailure(
                        OutboxEvent event,
                        Exception ex) {

                String errorMessage = ex.getMessage() != null
                                ? ex.getMessage()
                                : ex.getClass().getSimpleName();

                int retryCount = event.getRetryCount();

                if (retryCount >= maxRetries) {
                        log.error(
                                        "Inventory outbox event exceeded max retries: eventId={}, retryCount={}, error={}",
                                        event.getId(),
                                        retryCount,
                                        errorMessage,
                                        ex);

                        repository.markPermanentlyFailed(
                                        event.getId(),
                                        instanceId.get(),
                                        errorMessage);
                        return;
                }

                long delaySeconds = calculateBackoff(retryCount);
                Instant nextRetryAt = Instant.now().plusSeconds(delaySeconds);

                repository.markFailed(event.getId(),instanceId.get(),nextRetryAt,errorMessage);

                log.error(
                                "Failed to publish inventory outbox event: eventId={}, retryCount={}, nextRetryAt={}, error={}",
                                event.getId(),
                                retryCount + 1,
                                nextRetryAt,
                                errorMessage,
                                ex);
        }

        private long calculateBackoff(int retryCount) {

                long base = 5L;

                long multiplier = 1L << Math.min(retryCount, 6);

                return Math.min(base * multiplier, maxRetryDelaySeconds);
        }
}
