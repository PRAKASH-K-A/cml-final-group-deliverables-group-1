package com.stocker;

import quickfix.Application;
import quickfix.Message;
import quickfix.Session;
import quickfix.SessionID;
import quickfix.DoNotSend;
import quickfix.FieldNotFound;
import quickfix.IncorrectDataFormat;
import quickfix.IncorrectTagValue;
import quickfix.RejectLogon;
import quickfix.UnsupportedMessageType;
import quickfix.field.*;

public class OrderApplication implements Application {
    
    private final OrderBroadcaster broadcaster;
    
    public OrderApplication(OrderBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }
    
    @Override
    public void onCreate(SessionID sessionId) {
        System.out.println("Session Created: " + sessionId);
    }
    
    @Override
    public void onLogon(SessionID sessionId) {
        System.out.println("[ORDER SERVICE] ? Logon successful: " + sessionId);
        System.out.println("[ORDER SERVICE] Client connected - Ready to accept orders");
    }
    
    @Override
    public void onLogout(SessionID sessionId) {
        System.out.println("[ORDER SERVICE] ? LOGOUT: " + sessionId);
    }
    
    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        // Used for administrative messages (Heartbeats, Logons)
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            System.out.println("[ORDER SERVICE] ? Sending admin message: " + msgType);
        } catch (FieldNotFound e) {
            System.out.println("[ORDER SERVICE] ? Sending admin message");
        }
    }
    
    @Override
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound,
            IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        // Received administrative messages
        try {
            String msgType = message.getHeader().getString(MsgType.FIELD);
            System.out.println("[ORDER SERVICE] ? Received admin message: " + msgType);
        } catch (FieldNotFound e) {
            System.out.println("[ORDER SERVICE] ? Received admin message");
        }
    }
    
    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {
        // Outgoing business messages
        System.out.println("[ORDER SERVICE] ? Sending business message: " + message.toString());
    }
    
    @Override
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound,
            IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        // 1. Identify Message Type
        String msgType = message.getHeader().getString(MsgType.FIELD);
        
        if (msgType.equals(MsgType.ORDER_SINGLE)) {
            processNewOrder(message, sessionId);
        } else {
            System.out.println("[ORDER SERVICE] Received unknown message type: " + msgType);
        }
    }
    
    /**
     * Process incoming NewOrderSingle (MsgType=D) messages
     */
    private void processNewOrder(Message message, SessionID sessionId) {
        try {
            // 2. Extract Fields using QuickFIX types
            String clOrdId = message.getString(ClOrdID.FIELD);
            String symbol = message.getString(Symbol.FIELD);
            char side = message.getChar(Side.FIELD);
            double qty = message.getDouble(OrderQty.FIELD);
            double price = message.getDouble(Price.FIELD);
            
            System.out.printf("[ORDER SERVICE] ORDER RECEIVED: ID=%s Side=%s Sym=%s Px=%.2f Qty=%.0f%n",
                    clOrdId, (side == '1' ? "BUY" : "SELL"), symbol, price, qty);
            
            // 3. Create Order POJO for broadcasting to UI
            Order order = new Order(clOrdId, symbol, side, price, qty);
            
            // 4. Broadcast to Angular UI via WebSocket
            broadcaster.broadcastOrder(order);
            
            // 5. Validation (Simple Rule: Price and Qty must be positive)
            if (qty <= 0 || price <= 0) {
                sendReject(message, sessionId, "Invalid Price or Qty");
            } else {
                acceptOrder(message, sessionId);
            }
        } catch (FieldNotFound e) {
            System.err.println("[ORDER SERVICE] ERROR: Missing required field - " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Accept the order and send ExecutionReport with Status=NEW
     */
    private void acceptOrder(Message request, SessionID sessionId) {
        try {
            // Create an ExecutionReport (MsgType = 8)
            quickfix.fix44.ExecutionReport ack = new quickfix.fix44.ExecutionReport();
            
            // Mandatory Fields mapping
            ack.set(new OrderID("GEN_" + System.currentTimeMillis())); // Server generated ID
            ack.set(new ExecID("EXEC_" + System.currentTimeMillis()));
            ack.set(new ClOrdID(request.getString(ClOrdID.FIELD))); // Echo back the Client's ID
            ack.set(new Symbol(request.getString(Symbol.FIELD)));
            ack.set(new Side(request.getChar(Side.FIELD)));
            
            // Status fields: "New"
            ack.set(new ExecType(ExecType.NEW));
            ack.set(new OrdStatus(OrdStatus.NEW));
            
            // Quantity accounting
            ack.set(new LeavesQty(request.getDouble(OrderQty.FIELD)));
            ack.set(new CumQty(0));
            ack.set(new AvgPx(0));
            
            // Send back to the specific session
            Session.sendToTarget(ack, sessionId);
            
            System.out.println("[ORDER SERVICE] ? ORDER ACCEPTED: ClOrdID=" + request.getString(ClOrdID.FIELD));
        } catch (Exception e) {
            System.err.println("[ORDER SERVICE] ERROR: Failed to send acknowledgment");
            e.printStackTrace();
        }
    }
    
    /**
     * Reject the order and send ExecutionReport with Status=REJECTED
     */
    private void sendReject(Message request, SessionID sessionId, String reason) {
        try {
            quickfix.fix44.ExecutionReport reject = new quickfix.fix44.ExecutionReport();
            
            // Mandatory Fields
            reject.set(new OrderID("REJ_" + System.currentTimeMillis()));
            reject.set(new ExecID("EXEC_" + System.currentTimeMillis()));
            reject.set(new ClOrdID(request.getString(ClOrdID.FIELD)));
            reject.set(new Symbol(request.getString(Symbol.FIELD)));
            reject.set(new Side(request.getChar(Side.FIELD)));
            
            // Status fields: "Rejected"
            reject.set(new ExecType(ExecType.REJECTED));
            reject.set(new OrdStatus(OrdStatus.REJECTED));
            
            // Rejection reason
            reject.set(new Text(reason));
            
            // Quantity fields
            reject.set(new LeavesQty(0));
            reject.set(new CumQty(0));
            reject.set(new AvgPx(0));
            
            Session.sendToTarget(reject, sessionId);
            
            System.out.println("[ORDER SERVICE] ? ORDER REJECTED: ClOrdID=" + 
                    request.getString(ClOrdID.FIELD) + " Reason: " + reason);
        } catch (Exception e) {
            System.err.println("[ORDER SERVICE] ERROR: Failed to send rejection");
            e.printStackTrace();
        }
    }
}
