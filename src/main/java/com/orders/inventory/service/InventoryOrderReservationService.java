package com.orders.inventory.service;

import com.orders.inventory.common.events.OrderCreatedEvent;

public interface InventoryOrderReservationService {

    void reserveOrderInventory(OrderCreatedEvent event);
}
