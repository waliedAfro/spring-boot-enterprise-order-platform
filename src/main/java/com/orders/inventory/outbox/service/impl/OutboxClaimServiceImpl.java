package com.orders.inventory.outbox.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Pageable;

import com.orders.inventory.outbox.domain.model.OutboxEvent;
import com.orders.inventory.outbox.domain.model.OutboxStatus;
import com.orders.inventory.outbox.service.OutboxClaimService;
import com.orders.inventory.outbox.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;

@Service 
@RequiredArgsConstructor 
public class OutboxClaimServiceImpl implements OutboxClaimService{

    private final OutboxEventRepository repository;

	@Override
        @Transactional 
	public List<OutboxEvent> claim(Instant now, Instant processingTimeOut, int batchSize, String instanceId) {
		
        Pageable pageable =
                PageRequest.of(
                        0,
                        batchSize
                );
        List<OutboxEvent> candidates =
                repository.findEventsForClaim(now,processingTimeOut,pageable);

        List<OutboxEvent> claimed = new ArrayList<>();

        for (OutboxEvent event : candidates) {

            int updated =
                    repository.markProcessing( event.getId(),
                instanceId,
                now,
                processingTimeOut);

            if (updated == 1) {
                event.setStatus(OutboxStatus.PROCESSING);
                event.setProcessingInstanceId(instanceId);
                event.setProcessingAt(now);

                claimed.add(event);
            }
        }

        return claimed;
	}

}
