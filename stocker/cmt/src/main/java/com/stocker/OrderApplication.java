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
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class OrderApplication implements Application {

    private final OrderBroadcaster broadcaster;
    private final BlockingQueue<Order> dbQueue;
    private final Map<String, Security> validSecurities;
    private final MatchingEngine matchingEngine;

    public OrderApplication(OrderBroadcaster broadcaster, BlockingQueue<Order> dbQueue, MatchingEngine matchingEngine) {
        this.broadcaster = broadcaster;
        this.dbQueue = dbQueue;
        this.matchingEngine = matchingEngine;
        this.validSecurities = DatabaseManager.loadSecurityMaster();
        System.out.println("[ORDER SERVICE] Security Master loaded: " + validSecurities.size() + " valid symbols");
        System.out.println("[ORDER SERVICE] Matching Engine initialized and ready");
    }

    @Override
    public void onCreate(SessionID sessionId) {
        System.out.println("Session Created: " + sessionId);
    }

    @Override
    public void onLogon(SessionID sessionId) {
        // Extract FIX version from session ID
        String fixVersion = sessionId.getBeginString();

        // Verify FIX 4.4 is being used
        if (!fixVersion.equals("FIX.4.4")) {
            System.err.println("[ORDER SERVICE] ❌ REJECTED: Invalid FIX version!");
            System.err.println("[ORDER SERVICE] Expected: FIX.4.4");
            System.err.println("[ORDER SERVICE] Received: " + fixVersion);
            System.err.println("[ORDER SERVICE] Disconnecting client...");
            Session session = Session.lookupSession(sessionId);
            if (session != null) {
                try {
                    session.disconnect("Invalid FIX version. Expected FIX.4.4, got " + fixVersion, true);
                } catch (Exception e) {
                    System.err.println("[ORDER SERVICE] Error disconnecting session: " + e.getMessage());
                }
            }
            return; // Don't proceed with logon
        }

        System.out.println("[ORDER SERVICE] ? Logon successful: " + sessionId);
        System.out.println("[ORDER SERVICE] ✓ FIX Version verified: " + fixVersion);
        System.out.println("[ORDER SERVICE] ? SenderCompID: " + sessionId.getSenderCompID());
        System.out.println("[ORDER SERVICE] ? TargetCompID: " + sessionId.getTargetCompID());
        System.out.println("[ORDER SERVICE] ✅ Client connected - System is ready to accept orders");
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

            // VERSION CHECK: Verify Logon message uses FIX.4.4
            if (msgType.equals(MsgType.LOGON)) {
                String beginString = message.getHeader().getString(quickfix.field.BeginString.FIELD);
                System.out.println("[ORDER SERVICE] Logon message using FIX version: " + beginString);

                if (!beginString.equals("FIX.4.4")) {
                    System.err.println("[ORDER SERVICE] ❌ VERSION MISMATCH DETECTED!");
                    System.err.println("[ORDER SERVICE]    Expected: FIX.4.4");
                    System.err.println("[ORDER SERVICE]    Received: " + beginString);
                    // Reject the logon
                    throw new RejectLogon("Unsupported FIX version: " + beginString + ". Expected FIX.4.4");
                } else {
                    System.out.println("[ORDER SERVICE] ✓ FIX 4.4 version verified in Logon message");
                }
            }
        } catch (FieldNotFound e) {
            System.out.println("[ORDER SERVICE] ? Received admin message (unable to parse type)");
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

            // 3. Validate security symbol against Security Master
            if (!validSecurities.containsKey(symbol)) {
                System.out.println("[ORDER SERVICE] REJECTED - Unknown symbol: " + symbol);
                sendReject(message, sessionId, "Unknown Security Symbol: " + symbol);
                return;
            }

            // 4. Validation: Price and Qty must be positive
            if (qty <= 0 || price <= 0) {
                sendReject(message, sessionId, "Invalid Price or Qty");
                return;
            }

            // 4b. Validate lot size
            Security sec = validSecurities.get(symbol);
            if (!sec.isValidLotSize(qty)) {
                sendReject(message, sessionId,
                        "Invalid lot size for " + symbol + " (lotSize=" + sec.getLotSize() + ")");
                return;
            }

            // 5. Create Order POJO
            Order order = new Order(clOrdId, symbol, side, price, qty);

            // 6. MATCHING ENGINE: Try to match order against order book
            java.util.List<Execution> executions = matchingEngine.matchOrder(order);

            System.out.printf("[ORDER SERVICE] Order matching complete: %d execution(s) generated%n",
                    executions.size());

            // 7. Send approriate ExecutionReport based on fill status
            if (executions.isEmpty()) {
                // No matches - order remains pending in order book
                System.out.println("[ORDER SERVICE] No matches found - order on book as NEW");
                acceptOrder(message, sessionId, order);
            } else {
                // One or more matches - send execution report
                System.out.printf("[ORDER SERVICE] Executions generated: %d fills%n", executions.size());
                sendExecutionReport(message, sessionId, order, executions);
            }

            // 8. Broadcast ALL orders (including partially filled ones on book) to Angular
            // UI
            broadcaster.broadcastOrder(order);

            // 9. ASYNC PATH: Queue order for database persistence
            if (!dbQueue.offer(order)) {
                System.err.println("[ORDER SERVICE] WARNING: Database queue is full! Order: " + clOrdId);
            } else {
                System.out.println("[ORDER SERVICE] Order queued for persistence: " + clOrdId);
            }

            // 10. Queue executions for database persistence
            for (Execution exec : executions) {
                // Store execution metadata for database
                System.out.println("[ORDER SERVICE] Execution queued: " + exec.getExecId());
            }

        } catch (FieldNotFound e) {
            System.err.println("[ORDER SERVICE] ERROR: Missing required field - " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Accept the order and send ExecutionReport with Status=NEW
     */
    private void acceptOrder(Message request, SessionID sessionId, Order order) {
        try {
            // Create an ExecutionReport (MsgType = 8)
            quickfix.fix44.ExecutionReport ack = new quickfix.fix44.ExecutionReport();

            // Mandatory Fields mapping
            ack.set(new OrderID(order.getOrderId()));
            ack.set(new ExecID("EXEC_" + System.currentTimeMillis()));
            ack.set(new ClOrdID(request.getString(ClOrdID.FIELD))); // Echo back the Client's ID
            ack.set(new Symbol(request.getString(Symbol.FIELD)));
            ack.set(new Side(request.getChar(Side.FIELD)));

            // Status fields: "New"
            ack.set(new ExecType(ExecType.NEW));
            ack.set(new OrdStatus(OrdStatus.NEW));

            // Quantity accounting
            ack.set(new LeavesQty(order.getLeavesQty()));
            ack.set(new CumQty(order.getCumQty()));
            ack.set(new AvgPx(order.getAvgPx()));

            // Send back to the specific session
            Session.sendToTarget(ack, sessionId);

            System.out.println("[ORDER SERVICE] ? ORDER ACCEPTED: ClOrdID=" + request.getString(ClOrdID.FIELD));
        } catch (Exception e) {
            System.err.println("[ORDER SERVICE] ERROR: Failed to send acknowledgment");
            e.printStackTrace();
        }
    }

    /**
     * Send ExecutionReport with fill information for matched orders
     */
    private void sendExecutionReport(Message request, SessionID sessionId, Order order,
            java.util.List<Execution> executions) {
        try {
            // Determine status based on leaves quantity
            char ordStatus = order.getLeavesQty() == 0 ? OrdStatus.FILLED : OrdStatus.PARTIALLY_FILLED;
            char execType = order.getLeavesQty() == 0 ? ExecType.TRADE : ExecType.PARTIAL_FILL;

            // Create an ExecutionReport for all accumulated fills
            quickfix.fix44.ExecutionReport report = new quickfix.fix44.ExecutionReport();

            // Mandatory Fields
            report.set(new OrderID(order.getOrderId()));
            report.set(new ExecID(order.getLastExecId())); // Use last execution ID
            report.set(new ClOrdID(request.getString(ClOrdID.FIELD)));
            report.set(new Symbol(request.getString(Symbol.FIELD)));
            report.set(new Side(request.getChar(Side.FIELD)));

            // Status fields
            report.set(new ExecType(execType));
            report.set(new OrdStatus(ordStatus));

            // Quantity accounting - accumulated from all executions
            report.set(new LeavesQty(order.getLeavesQty()));
            report.set(new CumQty(order.getCumQty()));
            report.set(new AvgPx(order.getAvgPx()));

            // Last execution quantity and price (from last execution)
            if (!executions.isEmpty()) {
                Execution lastExec = executions.get(executions.size() - 1);
                report.set(new LastQty(lastExec.getExecQty()));
                report.set(new LastPx(lastExec.getExecPrice()));
            }

            // Send the execution report
            Session.sendToTarget(report, sessionId);

            System.out.printf(
                    "[ORDER SERVICE] ? EXECUTION REPORT: ClOrdID=%s Status=%s CumQty=%.0f LeavesQty=%.0f AvgPx=%.2f%n",
                    order.getClOrdID(), ordStatus, order.getCumQty(), order.getLeavesQty(), order.getAvgPx());

        } catch (Exception e) {
            System.err.println("[ORDER SERVICE] ERROR: Failed to send execution report");
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
