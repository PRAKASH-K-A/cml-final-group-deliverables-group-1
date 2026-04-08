package com.stocker;

import quickfix.DefaultMessageFactory;
import quickfix.FileStoreFactory;
import quickfix.ScreenLogFactory;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class AppLauncher {

    public static void main(String[] args) {
        try {
            System.out.println("=".repeat(60));
            System.out.println(" ORDER MANAGEMENT SYSTEM - STARTUP ");
            System.out.println("=".repeat(60));

            // Step 1: Test Database Connection
            if (!DatabaseManager.testConnection()) {
                System.err.println("[STARTUP] FATAL: Cannot connect to PostgreSQL!");
                System.err.println("[STARTUP] Please check POSTGRESQL_SETUP.md and verify:");
                System.err.println("  1. PostgreSQL is running");
                System.err.println("  2. Database 'trading_system' exists");
                System.err.println("  3. Username/password in DatabaseManager.java are correct");
                return;
            }

            // Step 2: Create Shared Queue for Asynchronous Database Writes
            BlockingQueue<Order> dbQueue = new LinkedBlockingQueue<>(10000);
            System.out.println("[STARTUP] ✓ Database queue created (capacity: 10,000 orders)");

            // Step 3: Start Database Persistence Worker Thread
            OrderPersister persister = new OrderPersister(dbQueue);
            Thread persisterThread = new Thread(persister, "DB-Persister-Thread");
            persisterThread.setDaemon(false); // Keeps running until queue is drained
            persisterThread.start();

            // Step 4: Initialize WebSocket Server for Real-time UI Updates
            OrderBroadcaster broadcaster = new OrderBroadcaster(8080);
            broadcaster.start();

            // Step 5: Initialize Matching Engine for order matching
            MatchingEngine matchingEngine = new MatchingEngine();
            System.out.println("[STARTUP] ✓ Matching Engine initialized");

            // Step 6: Load and Verify FIX Configuration
            SessionSettings settings = new SessionSettings("order-service.cfg");
            System.out.println("[STARTUP] ✓ FIX configuration loaded from 'order-service.cfg'");

            // Step 7: Perform System Health Check (including FIX version verification)
            if (!SystemHealthCheck.performStartupHealthCheck(settings)) {
                System.err.println("[STARTUP] FATAL: System health check failed!");
                System.err.println("[STARTUP] System will not accept connections until issues are resolved.");
                return;
            }

            // Step 8: Initialize FIX Engine Components (now safe to proceed)
            OrderApplication application = new OrderApplication(broadcaster, dbQueue, matchingEngine);
            FileStoreFactory storeFactory = new FileStoreFactory(settings);
            ScreenLogFactory logFactory = new ScreenLogFactory(settings);
            DefaultMessageFactory messageFactory = new DefaultMessageFactory();

            // Step 9: Start FIX Acceptor (listens for FIX connections)
            SocketAcceptor acceptor = new SocketAcceptor(application, storeFactory, settings,
                    logFactory, messageFactory);
            acceptor.start();
            System.out.println("[ORDER SERVICE] ✓ FIX Order Service started. Listening on port 9876...");
            System.out.println("[ORDER SERVICE] ? FIX Version Required: FIX.4.4");
            System.out.println("[ORDER SERVICE] Waiting for MiniFix client connection...");

            // Final system ready notification
            System.out.println("\n" + "=".repeat(60));
            System.out.println(" ✅✅✅ SYSTEM IS READY ✅✅✅");
            System.out.println(" FIX 4.4 Validation Enabled - Only FIX.4.4 connections accepted");
            System.out.println(" Matching Engine - ACTIVE");
            System.out.println(" Database Persistence - ACTIVE");
            System.out.println(" WebSocket Broadcaster - ACTIVE");
            System.out.println("=".repeat(60));
            System.out.println("\n[SYSTEM] All components initialized and verified!");
            System.out.println("[SYSTEM] Press any key to shutdown...\n");

            // Keep the process running
            System.in.read();

            // Graceful Shutdown
            System.out.println("\n[SHUTDOWN] Initiating graceful shutdown...");
            acceptor.stop();
            System.out.println("[SHUTDOWN] ✓ FIX Acceptor stopped");

            broadcaster.stop();
            System.out.println("[SHUTDOWN] ✓ WebSocket server stopped");

            persister.stop();
            persisterThread.join(5000); // Wait up to 5 seconds for queue to drain
            System.out.println("[SHUTDOWN] ✓ Database persister stopped");

            System.out.println("[SHUTDOWN] Goodbye!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
