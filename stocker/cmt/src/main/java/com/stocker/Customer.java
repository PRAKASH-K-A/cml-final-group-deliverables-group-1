package com.stocker;

import java.math.BigDecimal;

/**
 * Customer - Entity representing a trading client (Customer Master)
 * 
 * Maps to the customer_master table in PostgreSQL.
 * Loaded at startup and cached in-memory for fast credit limit checks.
 */
public class Customer {
    
    private String customerCode;    // Primary key e.g. "CLIENT_A"
    private String customerName;    // Full name e.g. "Fidelity Investments"
    private String customerType;    // "INSTITUTIONAL" or "RETAIL"
    private BigDecimal creditLimit; // Maximum notional value allowed per order
    
    public Customer() {}
    
    public Customer(String customerCode, String customerName, String customerType, BigDecimal creditLimit) {
        this.customerCode = customerCode;
        this.customerName = customerName;
        this.customerType = customerType;
        this.creditLimit = creditLimit;
    }
    
    // --- Getters ---
    
    public String getCustomerCode() { return customerCode; }
    public String getCustomerName() { return customerName; }
    public String getCustomerType() { return customerType; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    
    // --- Setters ---
    
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }
    public void setCreditLimit(BigDecimal creditLimit) { this.creditLimit = creditLimit; }
    
    /**
     * Validates if a given notional value is within this customer's credit limit.
     * 
     * @param notional price * quantity of the order
     * @return true if within limit
     */
    public boolean isWithinCreditLimit(double notional) {
        if (creditLimit == null) return true; // No limit set = unlimited
        return BigDecimal.valueOf(notional).compareTo(creditLimit) <= 0;
    }
    
    /**
     * Whether this customer is institutional (higher limits, different rules)
     */
    public boolean isInstitutional() {
        return "INSTITUTIONAL".equalsIgnoreCase(customerType);
    }
    
    @Override
    public String toString() {
        return String.format("Customer{code='%s', name='%s', type='%s', creditLimit=%s}",
                customerCode, customerName, customerType, creditLimit);
    }
}
