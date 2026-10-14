package com.orders.inventory.exception;

import java.util.UUID;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID id) 
    { 
        super("Order not found: " + id); 
    } 
    
    public OrderNotFoundException(String orderNumber) 
    { 
        super("Order not found: " + orderNumber); 

    }

}
