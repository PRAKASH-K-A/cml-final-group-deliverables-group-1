package com.stocker;

import quickfix.DefaultMessageFactory;
import quickfix.FileStoreFactory;
import quickfix.ScreenLogFactory;
import quickfix.SessionSettings;
import quickfix.SocketAcceptor;

public class AppLauncher {
    
    public static void main(String[] args) {
        try {
            // Step 1: Initialize WebSocket Server for Real-time UI Updates
            OrderBroadcaster broadcaster = new OrderBroadcaster(8080);
            broadcaster.start();
            
            // Step 2: Initialize FIX Engine Components
            SessionSettings settings = new SessionSettings("order-service.cfg");
            OrderApplication application = new OrderApplication(broadcaster);
            FileStoreFactory storeFactory = new FileStoreFactory(settings);
            ScreenLogFactory logFactory = new ScreenLogFactory(settings);
            DefaultMessageFactory messageFactory = new DefaultMessageFactory();
            
            // Step 3: Start FIX Acceptor (listens for FIX connections)
            SocketAcceptor acceptor = new SocketAcceptor(application, storeFactory, settings,
                    logFactory, messageFactory);
            acceptor.start();
            System.out.println("[ORDER SERVICE] ✓ FIX Order Service started. Listening on port 9876...");
            System.out.println("[ORDER SERVICE] Waiting for MiniFix client connection...");
            System.out.println("=".repeat(60));
            
            // Keep the process running
            System.in.read();
            
            // Cleanup
            acceptor.stop();
            broadcaster.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
