package com.stocker;

import java.util.UUID;

public class Order {
    private String orderId; // Server-generated unique ID for database
    private String clOrdID; // Client Order ID (from FIX message)
    private String symbol;
    private char side; // '1' = BUY, '2' = SELL
    private double price;
    private double quantity;

    // Matching engine fields
    private double cumQty; // Cumulative executed quantity
    private double leavesQty; // Remaining quantity to be executed (quantity - cumQty)
    private double avgPx; // Weighted average execution price
    private String status; // NEW, PARTIALLY_FILLED, FILLED, CANCELLED
    private long timestamp; // Order creation timestamp
    private String lastExecId; // Last execution ID reference
    private long lastExecTime; // Last execution timestamp

    public Order() {
        this.orderId = "ORD_" + UUID.randomUUID().toString().substring(0, 8);
        this.cumQty = 0;
        this.leavesQty = 0;
        this.avgPx = 0;
        this.status = "NEW";
        this.timestamp = System.currentTimeMillis();
    }

    public Order(String clOrdID, String symbol, char side, double price, double quantity) {
        this.orderId = "ORD_" + UUID.randomUUID().toString().substring(0, 8);
        this.clOrdID = clOrdID;
        this.symbol = symbol;
        this.side = side;
        this.price = price;
        this.quantity = quantity;
        this.cumQty = 0;
        this.leavesQty = quantity;
        this.avgPx = 0;
        this.status = "NEW";
        this.timestamp = System.currentTimeMillis();
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getClOrdID() {
        return clOrdID;
    }

    public void setClOrdID(String clOrdID) {
        this.clOrdID = clOrdID;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public char getSide() {
        return side;
    }

    public void setSide(char side) {
        this.side = side;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public double getQuantity() {
        return quantity;
    }

    public void setQuantity(double quantity) {
        this.quantity = quantity;
        this.leavesQty = quantity - this.cumQty;
    }

    public double getCumQty() {
        return cumQty;
    }

    public void setCumQty(double cumQty) {
        this.cumQty = cumQty;
        this.leavesQty = this.quantity - cumQty;
    }

    public double getLeavesQty() {
        return leavesQty;
    }

    public void setLeavesQty(double leavesQty) {
        this.leavesQty = leavesQty;
    }

    public double getAvgPx() {
        return avgPx;
    }

    public void setAvgPx(double avgPx) {
        this.avgPx = avgPx;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getLastExecId() {
        return lastExecId;
    }

    public void setLastExecId(String lastExecId) {
        this.lastExecId = lastExecId;
    }

    public long getLastExecTime() {
        return lastExecTime;
    }

    public void setLastExecTime(long lastExecTime) {
        this.lastExecTime = lastExecTime;
    }

    @Override
    public String toString() {
        return "Order{" +
                "orderId='" + orderId + '\'' +
                ", clOrdID='" + clOrdID + '\'' +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", price=" + price +
                ", quantity=" + quantity +
                ", cumQty=" + cumQty +
                ", leavesQty=" + leavesQty +
                ", avgPx=" + avgPx +
                ", status='" + status + '\'' +
                '}';
    }
}
