package com.stocker;

import java.util.UUID;

/**
 * Represents a single trade execution between a buyer and seller order.
 * Multiple executions can be aggregated into a single Trade object.
 */
public class Execution {
    private String execId; // Unique execution ID
    private String orderId; // Order that was matched (can be buy or sell)
    private String oppositeOrderId; // The counter-party order
    private String symbol;
    private double execQty; // Quantity matched in this execution
    private double execPrice; // Price at which execution occurred
    private long execTime; // Timestamp of execution
    private char side; // Side of the primary order ('1'=BUY, '2'=SELL)

    public Execution() {
        this.execId = "EXEC_" + UUID.randomUUID().toString().substring(0, 12);
        this.execTime = System.currentTimeMillis();
    }

    public Execution(String orderId, String oppositeOrderId, String symbol,
            double execQty, double execPrice, char side) {
        this.execId = "EXEC_" + UUID.randomUUID().toString().substring(0, 12);
        this.orderId = orderId;
        this.oppositeOrderId = oppositeOrderId;
        this.symbol = symbol;
        this.execQty = execQty;
        this.execPrice = execPrice;
        this.side = side;
        this.execTime = System.currentTimeMillis();
    }

    // Getters and Setters
    public String getExecId() {
        return execId;
    }

    public void setExecId(String execId) {
        this.execId = execId;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getOppositeOrderId() {
        return oppositeOrderId;
    }

    public void setOppositeOrderId(String oppositeOrderId) {
        this.oppositeOrderId = oppositeOrderId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public double getExecQty() {
        return execQty;
    }

    public void setExecQty(double execQty) {
        this.execQty = execQty;
    }

    public double getExecPrice() {
        return execPrice;
    }

    public void setExecPrice(double execPrice) {
        this.execPrice = execPrice;
    }

    public long getExecTime() {
        return execTime;
    }

    public void setExecTime(long execTime) {
        this.execTime = execTime;
    }

    public char getSide() {
        return side;
    }

    public void setSide(char side) {
        this.side = side;
    }

    @Override
    public String toString() {
        return "Execution{" +
                "execId='" + execId + '\'' +
                ", orderId='" + orderId + '\'' +
                ", oppositeOrderId='" + oppositeOrderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", execQty=" + execQty +
                ", execPrice=" + execPrice +
                ", side=" + side +
                '}';
    }
}
