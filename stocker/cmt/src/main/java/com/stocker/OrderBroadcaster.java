package com.stocker;

import org.java_websocket.server.WebSocketServer;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import com.google.gson.Gson;
import java.net.InetSocketAddress;

/**
 * OrderBroadcaster - WebSocket Server for Real-time Order Broadcasting
 * 
 * This server maintains persistent connections with Angular UI clients
 * and pushes order data in JSON format whenever new orders arrive via FIX.
 */
public class OrderBroadcaster extends WebSocketServer {
    
    private final Gson gson;
    
    public OrderBroadcaster(int port) {
        super(new InetSocketAddress(port));
        this.gson = new Gson();
    }
    
    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientAddress = conn.getRemoteSocketAddress().toString();
        System.out.println("[WEBSOCKET] ✓ UI Connected: " + clientAddress);
        System.out.println("[WEBSOCKET] Active connections: " + getConnections().size());
    }
    
    @Override
    public void onMessage(WebSocket conn, String message) {
        // Generally, we don't expect messages FROM the UI in this lab
        // But we can log them if they arrive
        System.out.println("[WEBSOCKET] Received message from UI: " + message);
    }
    
    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String clientAddress = conn.getRemoteSocketAddress().toString();
        System.out.println("[WEBSOCKET] ✗ UI Disconnected: " + clientAddress);
        System.out.println("[WEBSOCKET] Reason: " + reason + " (Code: " + code + ")");
        System.out.println("[WEBSOCKET] Active connections: " + getConnections().size());
    }
    
    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("[WEBSOCKET] ERROR: " + ex.getMessage());
        ex.printStackTrace();
        
        if (conn != null) {
            System.err.println("[WEBSOCKET] Error on connection: " + conn.getRemoteSocketAddress());
        }
    }
    
    @Override
    public void onStart() {
        System.out.println("[WEBSOCKET] ✓ WebSocket Server started on port " + getPort());
        System.out.println("[WEBSOCKET] Ready to accept UI connections on ws://localhost:" + getPort());
    }
    
    /**
     * Broadcast an Order object to all connected UI clients
     * Converts the Order POJO to JSON and sends to all active connections
     * 
     * @param order The Order object to broadcast
     */
    public void broadcastOrder(Order order) {
        try {
            // Convert Order object to JSON string
            String json = gson.toJson(order);
            
            // Send to all connected UIs
            broadcast(json);
            
            System.out.println("[WEBSOCKET] ✓ Broadcasted order to " + 
                    getConnections().size() + " client(s): " + order.getClOrdID());
        } catch (Exception e) {
            System.err.println("[WEBSOCKET] ERROR: Failed to broadcast order - " + e.getMessage());
            e.printStackTrace();
        }
    }
}
