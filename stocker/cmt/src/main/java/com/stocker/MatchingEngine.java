package com.stocker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MatchingEngine - Core order matching logic for all symbols
 * 
 * Responsibilities:
 * 1. Maintain order book per symbol
 * 2. Match incoming orders against opposite side
 * 3. Generate executions with price-time priority
 * 4. Update order status and quantities
 * 5. Handle order cancellations
 * 
 * Thread Safety: Lock per symbol to prevent race conditions
 */
public class MatchingEngine {

    private final ConcurrentHashMap<String, OrderBook> orderBooks;
    private final AtomicLong executionCounter;

    public MatchingEngine() {
        this.orderBooks = new ConcurrentHashMap<>();
        this.executionCounter = new AtomicLong(0);
    }

    /**
     * Get or create an order book for a symbol
     */
    private OrderBook getOrCreateOrderBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, s -> {
            System.out.println("[MatchingEngine] Created new order book for symbol: " + symbol);
            return new OrderBook(symbol);
        });
    }

    /**
     * Main entry point: Match an incoming order against the order book
     * 
     * Returns a list of executions (0 or more).
     * Updates the order object in place (status, cumQty, leavesQty, avgPx).
     */
    public List<Execution> matchOrder(Order incomingOrder) {
        OrderBook book = getOrCreateOrderBook(incomingOrder.getSymbol());
        List<Execution> executions = new ArrayList<>();

        // Lock on the symbol's order book to prevent concurrent matches
        synchronized (book) {
            executions = matchOrderInternal(incomingOrder, book);
        }

        return executions;
    }

    /**
     * Core matching algorithm
     */
    private List<Execution> matchOrderInternal(Order incomingOrder, OrderBook book) {
        List<Execution> executions = new ArrayList<>();

        double remainingQty = incomingOrder.getQuantity();

        if (incomingOrder.getSide() == '1') { // BUY order
            // Match against ASK orders (sellers)
            executions = matchBuyOrder(incomingOrder, book, remainingQty);
        } else if (incomingOrder.getSide() == '2') { // SELL order
            // Match against BID orders (buyers)
            executions = matchSellOrder(incomingOrder, book, remainingQty);
        }

        return executions;
    }

    /**
     * Match a BUY order against the ASK side (sellers)
     * Best prices first: lowest ASK prices (best for buyer)
     * Time priority: oldest orders at same price
     */
    private List<Execution> matchBuyOrder(Order buyOrder, OrderBook book, double remainingQty) {
        List<Execution> executions = new ArrayList<>();

        // Try to match against all ASK levels (lowest price first)
        for (Double askPrice : new ArrayList<>(book.getAsks().keySet())) {
            if (remainingQty == 0)
                break;

            // Check if prices cross: BUY price >= ASK price?
            if (buyOrder.getPrice() < askPrice) {
                // No match possible at this and higher ask prices
                break;
            }

            List<Order> askOrders = book.getAsks().get(askPrice);
            if (askOrders == null || askOrders.isEmpty())
                continue;

            // Match against orders at this price level (FIFO - oldest first)
            List<Order> ordersToRemove = new ArrayList<>();

            for (Order askOrder : askOrders) {
                if (remainingQty == 0)
                    break;

                // How much can we match?
                double matchQty = Math.min(remainingQty, askOrder.getLeavesQty());

                // Create execution
                Execution exec = new Execution(
                        buyOrder.getOrderId(),
                        askOrder.getOrderId(),
                        buyOrder.getSymbol(),
                        matchQty,
                        askPrice, // Match at ASK price (better for buyer)
                        '1' // BUY
                );
                executions.add(exec);

                // Update incoming BUY order
                double newCumQty = buyOrder.getCumQty() + matchQty;
                buyOrder.setCumQty(newCumQty);
                buyOrder.setLeavesQty(buyOrder.getQuantity() - newCumQty);
                updateAveragePriceForOrder(buyOrder, matchQty, askPrice);
                buyOrder.setLastExecId(exec.getExecId());
                buyOrder.setLastExecTime(exec.getExecTime());

                // Update matched ASK order
                double askNewCumQty = askOrder.getCumQty() + matchQty;
                askOrder.setCumQty(askNewCumQty);
                askOrder.setLeavesQty(askOrder.getQuantity() - askNewCumQty);
                updateAveragePriceForOrder(askOrder, matchQty, askPrice);
                askOrder.setLastExecId(exec.getExecId());
                askOrder.setLastExecTime(exec.getExecTime());

                remainingQty -= matchQty;

                // Mark ASK order for removal if fully matched
                if (askOrder.getLeavesQty() == 0) {
                    askOrder.setStatus("FILLED");
                    ordersToRemove.add(askOrder);
                } else {
                    askOrder.setStatus("PARTIALLY_FILLED");
                }

                System.out.println(String.format(
                        "[MATCH] BUY %s matched with SELL %s | Qty: %.0f @ $%.2f | BUY remaining: %.0f",
                        buyOrder.getClOrdID(), askOrder.getClOrdID(), matchQty, askPrice, remainingQty));
            }

            // Remove fully-matched ASK orders from book
            for (Order order : ordersToRemove) {
                askOrders.remove(order);
            }

            // Clean up empty price level
            if (askOrders.isEmpty()) {
                book.getAsks().remove(askPrice);
            }
        }

        // Update incoming order status
        if (buyOrder.getLeavesQty() == 0) {
            buyOrder.setStatus("FILLED");
        } else if (buyOrder.getCumQty() > 0) {
            buyOrder.setStatus("PARTIALLY_FILLED");
            // Add remaining order to book
            book.addOrder(buyOrder);
        } else {
            buyOrder.setStatus("NEW");
            // Add order to book (no matches)
            book.addOrder(buyOrder);
        }

        return executions;
    }

    /**
     * Match a SELL order against the BID side (buyers)
     * Best prices first: highest BID prices (best for seller)
     * Time priority: oldest orders at same price
     */
    private List<Execution> matchSellOrder(Order sellOrder, OrderBook book, double remainingQty) {
        List<Execution> executions = new ArrayList<>();

        // Try to match against all BID levels (highest price first)
        for (Double bidPrice : new ArrayList<>(book.getBids().keySet())) {
            if (remainingQty == 0)
                break;

            // Check if prices cross: SELL price <= BID price?
            if (sellOrder.getPrice() > bidPrice) {
                // No match possible at this and lower bid prices
                break;
            }

            List<Order> bidOrders = book.getBids().get(bidPrice);
            if (bidOrders == null || bidOrders.isEmpty())
                continue;

            // Match against orders at this price level (FIFO - oldest first)
            List<Order> ordersToRemove = new ArrayList<>();

            for (Order bidOrder : bidOrders) {
                if (remainingQty == 0)
                    break;

                // How much can we match?
                double matchQty = Math.min(remainingQty, bidOrder.getLeavesQty());

                // Create execution
                Execution exec = new Execution(
                        sellOrder.getOrderId(),
                        bidOrder.getOrderId(),
                        sellOrder.getSymbol(),
                        matchQty,
                        bidPrice, // Match at BID price (better for seller)
                        '2' // SELL
                );
                executions.add(exec);

                // Update incoming SELL order
                double newCumQty = sellOrder.getCumQty() + matchQty;
                sellOrder.setCumQty(newCumQty);
                sellOrder.setLeavesQty(sellOrder.getQuantity() - newCumQty);
                updateAveragePriceForOrder(sellOrder, matchQty, bidPrice);
                sellOrder.setLastExecId(exec.getExecId());
                sellOrder.setLastExecTime(exec.getExecTime());

                // Update matched BID order
                double bidNewCumQty = bidOrder.getCumQty() + matchQty;
                bidOrder.setCumQty(bidNewCumQty);
                bidOrder.setLeavesQty(bidOrder.getQuantity() - bidNewCumQty);
                updateAveragePriceForOrder(bidOrder, matchQty, bidPrice);
                bidOrder.setLastExecId(exec.getExecId());
                bidOrder.setLastExecTime(exec.getExecTime());

                remainingQty -= matchQty;

                // Mark BID order for removal if fully matched
                if (bidOrder.getLeavesQty() == 0) {
                    bidOrder.setStatus("FILLED");
                    ordersToRemove.add(bidOrder);
                } else {
                    bidOrder.setStatus("PARTIALLY_FILLED");
                }

                System.out.println(String.format(
                        "[MATCH] SELL %s matched with BUY %s | Qty: %.0f @ $%.2f | SELL remaining: %.0f",
                        sellOrder.getClOrdID(), bidOrder.getClOrdID(), matchQty, bidPrice, remainingQty));
            }

            // Remove fully-matched BID orders from book
            for (Order order : ordersToRemove) {
                bidOrders.remove(order);
            }

            // Clean up empty price level
            if (bidOrders.isEmpty()) {
                book.getBids().remove(bidPrice);
            }
        }

        // Update incoming order status
        if (sellOrder.getLeavesQty() == 0) {
            sellOrder.setStatus("FILLED");
        } else if (sellOrder.getCumQty() > 0) {
            sellOrder.setStatus("PARTIALLY_FILLED");
            // Add remaining order to book
            book.addOrder(sellOrder);
        } else {
            sellOrder.setStatus("NEW");
            // Add order to book (no matches)
            book.addOrder(sellOrder);
        }

        return executions;
    }

    /**
     * Calculate weighted average price after each execution
     * Formula: (cumQty * oldAvgPx + matchQty * matchPrice) / (cumQty + matchQty)
     * But cumQty has already been updated, so:
     * avgPx = (cumQty * oldAvgPx + matchQty * matchPrice) / cumQty
     * We recalculate it as: sum of all (qty * price) / total qty
     */
    private void updateAveragePriceForOrder(Order order, double matchQty, double matchPrice) {
        double oldCumQty = order.getCumQty() - matchQty;
        double newCumQty = order.getCumQty();

        if (newCumQty > 0) {
            double totalValue = (oldCumQty * order.getAvgPx()) + (matchQty * matchPrice);
            order.setAvgPx(totalValue / newCumQty);
        }
    }

    /**
     * Cancel an order - remove from book and mark as cancelled
     */
    public boolean cancelOrder(String orderId, String symbol) {
        OrderBook book = orderBooks.get(symbol);
        if (book == null) {
            System.out.println("[MatchingEngine] Order book not found for symbol: " + symbol);
            return false;
        }

        synchronized (book) {
            // Find and remove the order
            Order order = findOrderInBook(book, orderId);
            if (order == null) {
                System.out.println("[MatchingEngine] Order not found: " + orderId);
                return false;
            }

            // Can only cancel if not already filled
            if (order.getStatus().equals("FILLED")) {
                System.out.println("[MatchingEngine] Cannot cancel FILLED order: " + orderId);
                return false;
            }

            book.removeOrder(order);
            order.setStatus("CANCELLED");
            System.out.println("[MatchingEngine] Cancelled order: " + orderId);
            return true;
        }
    }

    /**
     * Find an order in the book
     */
    private Order findOrderInBook(OrderBook book, String orderId) {
        // Search BIDs
        for (List<Order> orders : book.getBids().values()) {
            for (Order order : orders) {
                if (order.getOrderId().equals(orderId)) {
                    return order;
                }
            }
        }

        // Search ASKs
        for (List<Order> orders : book.getAsks().values()) {
            for (Order order : orders) {
                if (order.getOrderId().equals(orderId)) {
                    return order;
                }
            }
        }

        return null;
    }

    /**
     * Get the order book for a symbol (for monitoring/display)
     */
    public OrderBook getOrderBook(String symbol) {
        return orderBooks.get(symbol);
    }

    /**
     * Get all active order books
     */
    public ConcurrentHashMap<String, OrderBook> getAllOrderBooks() {
        return orderBooks;
    }

    /**
     * Print market data snapshot for all symbols
     */
    public void printMarketDataSnapshot() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println(" MARKET DATA SNAPSHOT - " + orderBooks.size() + " active symbols");
        System.out.println("=".repeat(70));

        for (OrderBook book : orderBooks.values()) {
            Double bid = book.getBestBid();
            Double ask = book.getBestAsk();
            Double spread = book.getSpread();

            System.out.printf("%-8s | Bid: $%-8.2f | Ask: $%-8.2f | Spread: $%-8.2f | " +
                    "BidQty: %d | AskQty: %d%n",
                    book.getSymbol(),
                    bid != null ? bid : 0,
                    ask != null ? ask : 0,
                    spread != null ? spread : 0,
                    book.getTotalBidOrders(),
                    book.getTotalAskOrders());
        }

        System.out.println("=".repeat(70) + "\n");
    }
}
