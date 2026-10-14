package com.orders.inventory.outbox.service.impl;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orders.inventory.outbox.domain.model.OutboxEvent;
import com.orders.inventory.outbox.domain.model.OutboxStatus;
import com.orders.inventory.outbox.repository.OutboxEventRepository;
import  com.orders.inventory.common.events.InventoryEventTypes;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor

public class OutboxEventService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${spring.kafka.topics.inventory-reserved}")
    private String inventoryReservedTopic;

    @Value("${spring.kafka.topics.inventory-reservation-failed}")
    private String inventoryReservationFailedTopic;

    @Value("${inventory.outbox.max-retries:10}")
    private int maxRetries;

    @Transactional
    public void saveInventoryReservedEvent(UUID orderId,Object event) {
        
        save(orderId,InventoryEventTypes.INVENTORY_RESERVED,inventoryReservedTopic,event);
    }

    @Transactional
    public void saveInventoryReservationFailedEvent(UUID orderId,Object event) {
        
        save(orderId,InventoryEventTypes.INVENTORY_RESERVATION_FAILED,inventoryReservationFailedTopic,event);
    }

    private void save(UUID aggregateId,String eventType,String topic,Object event) {
        try {

            String payload = objectMapper.writeValueAsString(event);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .eventId(UUID.randomUUID())
                    .eventType(eventType)
                    .topic(topic)
                    .aggregateId(aggregateId)
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .retryCount(0)
                    .maxRetries(maxRetries)
                    .nextRetryAt(Instant.now())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            outboxEventRepository.save(outboxEvent);

        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize inventory outbox event",
                    e
            );
        }
    }
}
