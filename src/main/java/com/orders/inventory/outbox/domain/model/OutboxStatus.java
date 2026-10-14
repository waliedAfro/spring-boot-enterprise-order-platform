package com.orders.inventory.outbox.domain.model;

public enum OutboxStatus {

    PENDING,
    PROCESSING,
    PUBLISHED,
    FAILED
}
