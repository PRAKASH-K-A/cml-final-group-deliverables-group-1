package com.stocker;

import java.util.List;

/**
 * TradeGenerator - Creates Trade objects from matching engine executions
 * 
 * Responsibility:
 * - Aggregate multiple executions into a single Trade object
 * - Calculate VWAP (Volume-Weighted Average Price) across all executions
 * - Identify buyer and seller order IDs from executions
 * 
 * Usage:
 * List<Execution> executions = matchingEngine.matchOrder(order);
 * Trade trade = TradeGenerator.generateTrade(order, executions);
 * DatabaseManager.insertTrade(trade);
 */
public class TradeGenerator {

    /**
     * Generate a Trade object from matching engine executions
     * 
     * @param incomingOrder The order that triggered the matches (buyer or seller)
     * @param executions    List of Execution objects from the matching engine
     * @return Trade object with VWAP calculated
     */
    public static Trade generateTrade(Order incomingOrder, List<Execution> executions) {
        if (executions == null || executions.isEmpty()) {
            return null;
        }

        // Determine buyer and seller from first execution
        Execution firstExec = executions.get(0);

        String buyOrderId;
        String sellOrderId;

        // The incoming order is either a BUY or SELL
        // Executions have orderId (the incoming order) and oppositeOrderId (the matched
        // order)

        if (incomingOrder.getSide() == '1') {
            // Incoming is a BUY order
            buyOrderId = incomingOrder.getOrderId();
            sellOrderId = firstExec.getOppositeOrderId(); // The order we matched against
        } else {
            // Incoming is a SELL order
            sellOrderId = incomingOrder.getOrderId();
            buyOrderId = firstExec.getOppositeOrderId(); // The order we matched against
        }

        // Create Trade object
        Trade trade = new Trade(incomingOrder.getSymbol(), buyOrderId, sellOrderId);

        // Add all executions to the trade
        // This automatically recalculates VWAP
        for (Execution exec : executions) {
            trade.addExecution(exec);
        }

        System.out.println("[TRADE GENERATOR] Trade created: " + trade.getTradeId() +
                " | Symbol: " + trade.getSymbol() +
                " | Buy Order: " + buyOrderId +
                " | Sell Order: " + sellOrderId +
                " | Total Qty: " + trade.getTotalQty() +
                " | VWAP: $" + String.format("%.2f", trade.getVwap()));

        return trade;
    }

    /**
     * Generate multiple Trade objects if there are multiple price levels matched
     * 
     * Useful for cases where one order sweeps multiple price levels
     * 
     * @param incomingOrder The incoming order
     * @param executions    All executions from matching
     * @return Array of Trade objects, one per price level matched
     */
    public static Trade[] generateTradesPerPriceLevel(Order incomingOrder, List<Execution> executions) {
        if (executions == null || executions.isEmpty()) {
            return new Trade[0];
        }

        // Group executions by price level
        // Key: execution price, Value: list of executions at that price
        java.util.Map<Double, java.util.List<Execution>> execsByPrice = new java.util.HashMap<>();

        for (Execution exec : executions) {
            execsByPrice.computeIfAbsent(exec.getExecPrice(), k -> new java.util.ArrayList<>())
                    .add(exec);
        }

        // Create one Trade per price level
        Trade[] trades = new Trade[execsByPrice.size()];
        int index = 0;

        for (java.util.List<Execution> priceExecutions : execsByPrice.values()) {
            trades[index++] = generateTrade(incomingOrder, priceExecutions);
        }

        return trades;
    }
}
