package com.orders.inventory.outbox.service;

import java.time.Instant;
import java.util.List;

import com.orders.inventory.outbox.domain.model.OutboxEvent;
public interface OutboxClaimService {

    List<OutboxEvent> claim(
            Instant now,
            Instant processingTimeOut,
            int batchSize,
            String instanceId
    );
}
