package com.orders.inventory.idempotency;


import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class IdempotencyService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(readOnly = true)
    public boolean alreadyProcessed(UUID eventId,String consumerName) {

        return processedEventRepository
                .existsByEventIdAndConsumerName(eventId,consumerName);
    }

    @Transactional
    public void markProcessed(UUID eventId,String consumerName) {

        processedEventRepository.save(
                ProcessedEvent.builder()
                        .eventId(eventId)
                        .consumerName(consumerName)
                        .processedAt(Instant.now())
                        .build()
        );
    }
}
