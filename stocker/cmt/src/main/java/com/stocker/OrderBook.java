package com.stocker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * OrderBook - In-Memory Order Book for a Single Symbol
 * 
 * Holds live, unmatched orders sorted by price-time priority.
 * This is the core data structure for the Matching Engine (Lab 7).
 * 
 * PRICE-TIME PRIORITY:
 * - Best BID = Highest price a buyer is willing to pay  (sorted DESC)
 * - Best ASK = Lowest price a seller is willing to accept (sorted ASC)
 * 
 * Uses ConcurrentSkipListMap for:
 *   1. Thread-safe concurrent access
 *   2. Automatic price-level sorting (O(log n) insert/remove)
 *   3. O(1) best bid/ask lookup via firstKey()
 */
public class OrderBook {
    
    private final String symbol;
    
    // Bids: Sorted HIGH to LOW (Descending) - Best Bid = firstKey()
    private final ConcurrentSkipListMap<Double, List<Order>> bids =
            new ConcurrentSkipListMap<>(Collections.reverseOrder());
    
    // Asks: Sorted LOW to HIGH (Ascending) - Best Ask = firstKey()
    private final ConcurrentSkipListMap<Double, List<Order>> asks =
            new ConcurrentSkipListMap<>();
    
    public OrderBook(String symbol) {
        this.symbol = symbol;
    }
    
    // -------------------------------------------------------------------------
    // Core Order Book Operations (implemented in Lab 7)
    // -------------------------------------------------------------------------
    
    /**
     * Add an order to the book at its price level.
     * Orders at the same price are queued FIFO (time priority).
     */
    public void addOrder(Order order) {
        if (order.getSide() == '1') { // BUY
            bids.computeIfAbsent(order.getPrice(), k -> new ArrayList<>()).add(order);
        } else {                       // SELL
            asks.computeIfAbsent(order.getPrice(), k -> new ArrayList<>()).add(order);
        }
        System.out.println(String.format("[ORDERBOOK] %s | Added %s %s @ %.2f | Bids: %d levels | Asks: %d levels",
                symbol,
                order.getSide() == '1' ? "BUY" : "SELL",
                order.getClOrdID(),
                order.getPrice(),
                bids.size(),
                asks.size()));
    }
    
    /**
     * Remove an order from the book (cancel or fill).
     */
    public void removeOrder(Order order) {
        ConcurrentSkipListMap<Double, List<Order>> side =
                order.getSide() == '1' ? bids : asks;
        
        List<Order> priceLevel = side.get(order.getPrice());
        if (priceLevel != null) {
            priceLevel.remove(order);
            if (priceLevel.isEmpty()) {
                side.remove(order.getPrice()); // Clean up empty price level
            }
        }
    }
    
    // -------------------------------------------------------------------------
    // Market Data Accessors
    // -------------------------------------------------------------------------
    
    /**
     * Best Bid: highest price a buyer is willing to pay.
     * Returns null if no bids exist.
     */
    public Double getBestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }
    
    /**
     * Best Ask: lowest price a seller is willing to accept.
     * Returns null if no asks exist.
     */
    public Double getBestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }
    
    /**
     * Spread: difference between best ask and best bid.
     * Returns null if one side is empty.
     */
    public Double getSpread() {
        Double bid = getBestBid();
        Double ask = getBestAsk();
        return (bid != null && ask != null) ? ask - bid : null;
    }
    
    /**
     * Check if a match is possible: bid >= ask (crossed book).
     */
    public boolean hasMatchableOrders() {
        Double bid = getBestBid();
        Double ask = getBestAsk();
        return bid != null && ask != null && bid >= ask;
    }
    
    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------
    
    public String getSymbol() { return symbol; }
    
    public ConcurrentSkipListMap<Double, List<Order>> getBids() { return bids; }
    
    public ConcurrentSkipListMap<Double, List<Order>> getAsks() { return asks; }
    
    public int getTotalBidOrders() {
        return bids.values().stream().mapToInt(List::size).sum();
    }
    
    public int getTotalAskOrders() {
        return asks.values().stream().mapToInt(List::size).sum();
    }
    
    /**
     * Print a human-readable snapshot of the order book.
     * Used for debugging and display.
     */
    public void printSnapshot() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println(" ORDER BOOK: " + symbol);
        System.out.println("=".repeat(50));
        
        System.out.println(" ASKS (Sell Orders):");
        if (asks.isEmpty()) {
            System.out.println("   <empty>");
        } else {
            // Print asks in reverse (highest first for readability)
            new ConcurrentSkipListMap<>(Collections.reverseOrder()).putAll(asks);
            for (Map.Entry<Double, List<Order>> level : asks.entrySet()) {
                System.out.printf("   $%8.2f  | %d order(s)%n", level.getKey(), level.getValue().size());
            }
        }
        
        System.out.println(" --- Spread: " + (getSpread() != null ? String.format("$%.2f", getSpread()) : "N/A") + " ---");
        
        System.out.println(" BIDS (Buy Orders):");
        if (bids.isEmpty()) {
            System.out.println("   <empty>");
        } else {
            for (Map.Entry<Double, List<Order>> level : bids.entrySet()) {
                System.out.printf("   $%8.2f  | %d order(s)%n", level.getKey(), level.getValue().size());
            }
        }
        System.out.println("=".repeat(50) + "\n");
    }
}
