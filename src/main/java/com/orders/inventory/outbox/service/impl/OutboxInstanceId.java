package com.orders.inventory.outbox.service.impl;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component 
public class OutboxInstanceId {

     private final String instanceId;

    public OutboxInstanceId(
        @Value("${spring.application.name:inventory-service}")
        String applicationName) {

        this.instanceId =
                applicationName + "-" + UUID.randomUUID();
    }

    public String get() {
        return instanceId;
    }
}
