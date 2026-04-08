package com.stocker;

/**
 * Security - Entity representing a tradable instrument (Security Master)
 * 
 * Maps to the security_master table in PostgreSQL.
 * Loaded at startup and cached in-memory for fast O(1) validation.
 */
public class Security {
    
    private String symbol;          // Primary key e.g. "AAPL"
    private String securityType;    // "CS" = Common Stock, "ETF", "OPT" = Option
    private String description;     // Full name e.g. "Apple Inc."
    private String underlying;      // For derivatives - underlying symbol
    private int lotSize;            // Minimum tradable quantity (usually 1 or 100)
    
    public Security() {}
    
    public Security(String symbol, String securityType, String description, int lotSize) {
        this.symbol = symbol;
        this.securityType = securityType;
        this.description = description;
        this.lotSize = lotSize;
    }
    
    // --- Getters ---
    
    public String getSymbol() { return symbol; }
    public String getSecurityType() { return securityType; }
    public String getDescription() { return description; }
    public String getUnderlying() { return underlying; }
    public int getLotSize() { return lotSize; }
    
    // --- Setters ---
    
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setSecurityType(String securityType) { this.securityType = securityType; }
    public void setDescription(String description) { this.description = description; }
    public void setUnderlying(String underlying) { this.underlying = underlying; }
    public void setLotSize(int lotSize) { this.lotSize = lotSize; }
    
    /**
     * Validates whether a given quantity is a valid lot size multiple.
     * e.g. if lotSize=100, qty=150 is INVALID, qty=200 is VALID.
     */
    public boolean isValidLotSize(double quantity) {
        return lotSize <= 1 || (quantity % lotSize == 0);
    }
    
    @Override
    public String toString() {
        return String.format("Security{symbol='%s', type='%s', desc='%s', lotSize=%d}",
                symbol, securityType, description, lotSize);
    }
}
