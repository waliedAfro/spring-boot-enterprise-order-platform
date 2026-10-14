package com.orders.inventory.exception;

import java.util.UUID;

public class CustomerNotFoundException extends RuntimeException {

    /*private CustomerNotFoundException(String message) 
    { 
        super(message); 
    } */
    

    public CustomerNotFoundException(UUID customerId) 
    { 
        super("Customer not found with id: " + customerId); 
    } 
    
    public CustomerNotFoundException(String customerNumber) 
    { 
        super("Customer not found with customer number: " + customerNumber); 
    } 

    public static CustomerNotFoundException byEmail(String email) 
    { 
        return new CustomerNotFoundException( "Customer not found with email: " + email );
    }
}

   