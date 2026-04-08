package com.stocker;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * OrderPersister - Asynchronous Database Writer
 * 
 * This class runs on a separate thread and continuously consumes
 * orders from a BlockingQueue, persisting them to PostgreSQL.
 * 
 * ARCHITECTURE BENEFITS:
 * - FIX Engine thread is NOT blocked by slow database I/O
 * - Orders are acknowledged immediately (low latency)
 * - Database writes happen asynchronously in the background
 * - Memory queue acts as a buffer during DB slowdowns
 * 
 * This is a standard pattern in high-frequency trading systems.
 */
public class OrderPersister implements Runnable {
    
    private final BlockingQueue<Order> orderQueue;
    private volatile boolean running = true;
    private final AtomicLong persistedCount = new AtomicLong(0);
    
    /**
     * Constructor
     * 
     * @param orderQueue Shared queue between FIX engine and database worker
     */
    public OrderPersister(BlockingQueue<Order> orderQueue) {
        this.orderQueue = orderQueue;
    }
    
    /**
     * Main worker thread loop
     * 
     * Continuously takes orders from the queue and persists them.
     * The take() method blocks when queue is empty, so no busy-waiting.
     */
    @Override
    public void run() {
        System.out.println("[PERSISTENCE] ✓ Database Worker Thread Started");
        System.out.println("[PERSISTENCE] Listening for orders on queue...");
        
        while (running) {
            try {
                // take() blocks until an order is available
                // This is efficient - no CPU spinning
                Order order = orderQueue.take();
                
                // Persist to PostgreSQL
                long startTime = System.nanoTime();
                DatabaseManager.insertOrder(order);
                long elapsedMicros = (System.nanoTime() - startTime) / 1000;
                
                // Track statistics
                long count = persistedCount.incrementAndGet();
                
                System.out.println(String.format(
                    "[PERSISTENCE] Order #%d persisted in %d μs | Queue size: %d | ClOrdID: %s",
                    count, elapsedMicros, orderQueue.size(), order.getClOrdID()
                ));
                
            } catch (InterruptedException e) {
                // Thread interrupted - graceful shutdown
                System.out.println("[PERSISTENCE] Worker thread interrupted");
                Thread.currentThread().interrupt();
                break;
                
            } catch (Exception e) {
                // Catch any other exceptions to prevent thread death
                System.err.println("[PERSISTENCE] ✗ Error persisting order: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        // Drain remaining orders before shutdown
        drainQueue();
        
        System.out.println("[PERSISTENCE] Worker thread stopped. Total persisted: " + persistedCount.get());
    }
    
    /**
     * Graceful shutdown - stop accepting new work
     */
    public void stop() {
        System.out.println("[PERSISTENCE] Shutdown signal received...");
        this.running = false;
    }
    
    /**
     * Drain any remaining orders in the queue before shutdown
     * 
     * This ensures no data loss when the application is stopped.
     */
    private void drainQueue() {
        int remaining = orderQueue.size();
        
        if (remaining > 0) {
            System.out.println("[PERSISTENCE] Draining " + remaining + " remaining orders...");
            
            while (!orderQueue.isEmpty()) {
                try {
                    Order order = orderQueue.poll();
                    if (order != null) {
                        DatabaseManager.insertOrder(order);
                        persistedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    System.err.println("[PERSISTENCE] Error draining order: " + e.getMessage());
                }
            }
            
            System.out.println("[PERSISTENCE] ✓ Queue drained successfully");
        }
    }
    
    /**
     * Get statistics about persisted orders
     * 
     * @return Total number of orders persisted
     */
    public long getPersistedCount() {
        return persistedCount.get();
    }
    
    /**
     * Get current queue size (for monitoring)
     * 
     * @return Number of orders waiting to be persisted
     */
    public int getQueueSize() {
        return orderQueue.size();
    }
}
