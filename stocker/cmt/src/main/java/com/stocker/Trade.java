package com.stocker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an aggregated trade containing one or more executions.
 * Calculates VWAP (Volume Weighted Average Price) across all executions.
 */
public class Trade {
    private String tradeId; // Unique trade ID
    private String symbol;
    private String buyOrderId; // Order ID of buyer
    private String sellOrderId; // Order ID of seller
    private List<Execution> executions; // All executions that make up this trade
    private double totalQty; // Total quantity traded
    private double vwap; // Volume-Weighted Average Price
    private long tradeTime; // Timestamp of trade

    public Trade() {
        this.tradeId = "TRADE_" + UUID.randomUUID().toString().substring(0, 12);
        this.executions = new ArrayList<>();
        this.tradeTime = System.currentTimeMillis();
    }

    public Trade(String symbol, String buyOrderId, String sellOrderId) {
        this.tradeId = "TRADE_" + UUID.randomUUID().toString().substring(0, 12);
        this.symbol = symbol;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.executions = new ArrayList<>();
        this.tradeTime = System.currentTimeMillis();
    }

    /**
     * Add an execution to this trade and recalculate VWAP
     */
    public void addExecution(Execution execution) {
        this.executions.add(execution);
        recalculateVWAP();
    }

    /**
     * Calculate Volume-Weighted Average Price
     * VWAP = sum(execQty * execPrice) / sum(execQty)
     */
    private void recalculateVWAP() {
        if (executions.isEmpty()) {
            this.vwap = 0;
            this.totalQty = 0;
            return;
        }

        double totalValue = 0;
        double totalQuantity = 0;

        for (Execution exec : executions) {
            totalValue += exec.getExecQty() * exec.getExecPrice();
            totalQuantity += exec.getExecQty();
        }

        this.totalQty = totalQuantity;
        this.vwap = totalQuantity > 0 ? totalValue / totalQuantity : 0;
    }

    // Getters and Setters
    public String getTradeId() {
        return tradeId;
    }

    public void setTradeId(String tradeId) {
        this.tradeId = tradeId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getBuyOrderId() {
        return buyOrderId;
    }

    public void setBuyOrderId(String buyOrderId) {
        this.buyOrderId = buyOrderId;
    }

    public String getSellOrderId() {
        return sellOrderId;
    }

    public void setSellOrderId(String sellOrderId) {
        this.sellOrderId = sellOrderId;
    }

    public List<Execution> getExecutions() {
        return executions;
    }

    public void setExecutions(List<Execution> executions) {
        this.executions = executions;
        recalculateVWAP();
    }

    public double getTotalQty() {
        return totalQty;
    }

    public double getVwap() {
        return vwap;
    }

    public long getTradeTime() {
        return tradeTime;
    }

    public void setTradeTime(long tradeTime) {
        this.tradeTime = tradeTime;
    }

    @Override
    public String toString() {
        return "Trade{" +
                "tradeId='" + tradeId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", totalQty=" + totalQty +
                ", vwap=" + vwap +
                ", execCount=" + executions.size() +
                '}';
    }
}
