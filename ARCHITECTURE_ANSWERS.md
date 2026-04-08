# ARCHITECTURE DESIGN ANSWERS - Trading Order System

## Q1: How will you implement data-ready or system status ready?

### Problem Context:

Your system currently accepts FIX connections even with wrong protocol version. Should validate FIX version before allowing orders and send explicit "System Ready" message.

### Solution: Version Validation & Health Check Handshake

#### Implementation Approach:

```java
// In OrderApplication.java - onLogon() method

@Override
public void onLogon(SessionID sessionId) {
    try {
        // 1. FIX VERSION VALIDATION
        String beginString = sessionId.getBeginString();
        if (!beginString.equals("FIX.4.4")) {
            System.err.println("[ORDER SERVICE] ✗ INCOMPATIBLE FIX VERSION: " + beginString);
            Session.sendToTarget(createVersionMismatchReject(sessionId), sessionId);
            throw new RejectLogon("Only FIX.4.4 supported. Received: " + beginString);
        }

        // 2. SYSTEM READINESS CHECK
        SystemHealthStatus health = SystemHealthCheck.performHealthCheck();
        if (!health.isReady()) {
            System.err.println("[ORDER SERVICE] ✗ SYSTEM NOT READY");
            sendSystemStatusMessage(sessionId, health);
            throw new RejectLogon("System not ready. See status message.");
        }

        // 3. SEND SYSTEM READY MESSAGE
        sendSystemReadyMessage(sessionId);
        System.out.println("[ORDER SERVICE] ✓ Logon successful: " + sessionId);
        System.out.println("[ORDER SERVICE] ✓ System READY - Accepting orders");

    } catch (Exception e) {
        System.err.println("[ORDER SERVICE] Login validation failed: " + e.getMessage());
    }
}

private void sendSystemReadyMessage(SessionID sessionId) {
    try {
        // Custom message or standard FIX Logon Response
        quickfix.fix44.Logon logonResponse = new quickfix.fix44.Logon();
        logonResponse.set(new HertBeatInt(30));
        logonResponse.set(new EncryptMethod(0)); // No encryption
        logonResponse.setString(9999, "SYSTEM_READY|DB:" + DatabaseManager.testConnection() +
                                      "|WS:" + OrderBroadcaster.isRunning() +
                                      "|SECURITIES:" + validSecurities.size());

        Session.sendToTarget(logonResponse, sessionId);
        System.out.println("[PROTOCOL] ✓ System Ready notification sent");
    } catch (Exception e) {
        e.printStackTrace();
    }
}
```

#### System Health Status Check:

```java
// New class: SystemHealthCheck.java

public class SystemHealthStatus {
    private boolean databaseReady;
    private boolean websocketReady;
    private boolean securityMasterLoaded;
    private int pendingOrders;
    private String lastError;

    public static SystemHealthStatus performHealthCheck() {
        SystemHealthStatus status = new SystemHealthStatus();

        // Check 1: Database connectivity
        status.databaseReady = DatabaseManager.testConnection();

        // Check 2: WebSocket server active
        status.websocketReady = OrderBroadcaster.isRunning();

        // Check 3: Security Master loaded
        status.securityMasterLoaded = !DatabaseManager.loadSecurityMaster().isEmpty();

        // Check 4: Critical dependencies
        if (!status.databaseReady) {
            status.lastError = "DATABASE_OFFLINE";
        } else if (!status.websocketReady) {
            status.lastError = "WEBSOCKET_OFFLINE";
        } else if (!status.securityMasterLoaded) {
            status.lastError = "SECURITY_MASTER_EMPTY";
        }

        return status;
    }

    public boolean isReady() {
        return databaseReady && websocketReady && securityMasterLoaded;
    }

    public String getStatus() {
        return String.format(
            "DB:%s|WS:%s|SEC:%s|Pending:%d",
            databaseReady ? "✓" : "✗",
            websocketReady ? "✓" : "✗",
            securityMasterLoaded ? "✓" : "✗",
            pendingOrders
        );
    }
}
```

#### Configuration Addition:

```properties
# In order-service.cfg

# FIX Protocol Version Enforcement
[SESSION]
BeginString=FIX.4.4
SenderCompID=EXEC_SERVER
TargetCompID=MINIFIX_CLIENT
# REJECT any other version
ValidateBeginString=Y

# Health Check Settings
HealthCheckInterval=60000  # Every 60 seconds
SystemReadyTimeout=5000    # 5 second startup timeout
```

### Key Design Points:

1. **Fail-fast on version mismatch** - Reject logon immediately
2. **Health check before accepting orders** - Don't accept orders if DB/WS down
3. **Explicit system ready notification** - Client knows when safe to send orders
4. **Atomic health check** - Check all services simultaneously
5. **Regular health monitoring** - Periodic checks (heartbeat interval)

---

## Q2: DB-Insertion service is not up and you should still be able to send in and process orders. How would you persist after the DB is back-up?

### Problem Context:

Database goes offline. Orders should still be accepted and processed. When DB comes back, backlog should be persisted.

### Solution: Dead Letter Queue (DLQ) with Circuit Breaker Pattern

#### Architecture:

```
Order → FIX Processing → WebSocket Broadcast → Database Queue
                                                      ↓
                                          [DB online?]
                                           ↙      ↖
                                        YES        NO
                                          ↓         ↓
                                    DB Insert   Dead Letter Queue (File)
                                                    ↓
                                    [DB comes back online]
                                                    ↓
                                          Replay DLQ → DB Insert
```

#### Implementation:

```java
// Modified OrderPersister.java with Circuit Breaker

public class OrderPersister implements Runnable {

    private final BlockingQueue<Order> dbQueue;
    private final File deadLetterFile;
    private volatile boolean isRunning = true;
    private CircuitBreaker circuitBreaker;
    private int failureCount = 0;
    private static final int FAILURE_THRESHOLD = 5;
    private static final long RETRY_DELAY = 30000; // 30 seconds

    public OrderPersister(BlockingQueue<Order> queue) {
        this.dbQueue = queue;
        this.deadLetterFile = new File("orders/dead-letter-queue.txt");
        this.circuitBreaker = new CircuitBreaker();
        this.deadLetterFile.getParentFile().mkdirs();
    }

    @Override
    public void run() {
        System.out.println("[DB-PERSISTER] ✓ Database persistence worker started");

        while (isRunning) {
            try {
                Order order = dbQueue.poll(5, TimeUnit.SECONDS);
                if (order == null) continue;

                // Attempt database insertion
                if (attemptDatabaseInsert(order)) {
                    failureCount = 0; // Reset on success
                    circuitBreaker.recordSuccess();
                } else {
                    failureCount++;
                    circuitBreaker.recordFailure();

                    // Circuit breaker OPEN - DB is down
                    if (failureCount >= FAILURE_THRESHOLD) {
                        System.err.println("[DB-PERSISTER] ✗ DB OFFLINE - Writing to Dead Letter Queue");
                        writeToDeadLetterQueue(order);

                        // Wait before retry
                        System.out.println("[DB-PERSISTER] Retrying in " + (RETRY_DELAY/1000) + " seconds...");
                        Thread.sleep(RETRY_DELAY);
                        failureCount = 0; // Reset counter for retry
                    } else {
                        // Circuit breaker HALF-OPEN - try again
                        System.err.println("[DB-PERSISTER] ⚠ DB error (attempt " + failureCount +
                                         "). Retrying...");
                        dbQueue.put(order); // Put back in queue for retry
                        Thread.sleep(5000); // Small delay before retry
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // GRACEFUL SHUTDOWN: Drain remaining orders
        System.out.println("[DB-PERSISTER] Draining remaining orders from queue...");
        drainRemainingOrders();
    }

    private boolean attemptDatabaseInsert(Order order) {
        try {
            // Test database health first
            if (!DatabaseManager.testConnection()) {
                return false;
            }

            DatabaseManager.insertOrder(order);
            System.out.println("[DB-PERSISTER] ✓ Order persisted: " + order.getClOrdID());
            return true;

        } catch (SQLException e) {
            System.err.println("[DB-PERSISTER] ✗ Database error: " + e.getMessage());
            return false;
        }
    }

    private void writeToDeadLetterQueue(Order order) {
        try {
            // Serialize order to JSON and append to DLQ file
            String json = new Gson().toJson(order);
            Files.write(
                deadLetterFile.toPath(),
                (json + "\n").getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
            System.out.println("[DB-PERSISTER] Stored in DLQ: " + order.getClOrdID());
        } catch (IOException e) {
            System.err.println("[DB-PERSISTER] CRITICAL: Cannot write to DLQ: " + e.getMessage());
        }
    }

    private void drainRemainingOrders() {
        List<Order> remaining = new ArrayList<>();
        dbQueue.drainTo(remaining);

        for (Order order : remaining) {
            try {
                if (!attemptDatabaseInsert(order)) {
                    writeToDeadLetterQueue(order);
                }
            } catch (Exception e) {
                writeToDeadLetterQueue(order);
            }
        }

        System.out.println("[DB-PERSISTER] Processed " + remaining.size() + " remaining orders");
    }

    public void replayDeadLetterQueue() {
        System.out.println("[DB-PERSISTER] ✓ Replaying Dead Letter Queue...");
        try {
            List<String> lines = Files.readAllLines(deadLetterFile.toPath());

            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                Order order = new Gson().fromJson(line, Order.class);
                if (attemptDatabaseInsert(order)) {
                    // Mark as processed (rewrite file without this line)
                    System.out.println("[DB-PERSISTER] ✓ DLQ Replay: " + order.getClOrdID());
                } else {
                    System.err.println("[DB-PERSISTER] ✗ DLQ Replay failed: " + order.getClOrdID());
                    break; // Stop if any insertion fails
                }
            }

            // Clear DLQ file on successful replay
            Files.deleteIfExists(deadLetterFile.toPath());
            System.out.println("[DB-PERSISTER] ✓ Dead Letter Queue cleared");

        } catch (IOException e) {
            System.err.println("[DB-PERSISTER] Error replaying DLQ: " + e.getMessage());
        }
    }

    public void stop() {
        isRunning = false;
    }
}

// Circuit Breaker Pattern

public class CircuitBreaker {
    enum State { CLOSED, OPEN, HALF_OPEN }

    private State state = State.CLOSED;
    private int failureCount = 0;
    private long lastFailureTime = 0;
    private static final int FAILURE_THRESHOLD = 5;
    private static final long TIMEOUT = 60000; // 60 seconds before retry

    public synchronized void recordSuccess() {
        state = State.CLOSED;
        failureCount = 0;
    }

    public synchronized void recordFailure() {
        failureCount++;
        lastFailureTime = System.currentTimeMillis();

        if (failureCount >= FAILURE_THRESHOLD) {
            state = State.OPEN; // Stop accepting calls
            System.err.println("[CIRCUIT-BREAKER] ✗ OPEN - Service unavailable");
        }
    }

    public synchronized boolean canAttempt() {
        if (state == State.CLOSED) return true;

        if (state == State.OPEN) {
            if (System.currentTimeMillis() - lastFailureTime > TIMEOUT) {
                state = State.HALF_OPEN;
                System.out.println("[CIRCUIT-BREAKER] ? HALF_OPEN - Attempting recovery");
                return true;
            }
            return false;
        }

        return state == State.HALF_OPEN;
    }
}
```

#### In AppLauncher - Add DLQ Replay on Startup:

```java
// Step 7: Replay Dead Letter Queue (orders that failed during downtime)
OrderPersister persister = new OrderPersister(dbQueue);
if (new File("orders/dead-letter-queue.txt").exists()) {
    System.out.println("[STARTUP] Found unprocessed orders. Replaying...");
    persister.replayDeadLetterQueue();
}
```

### Key Design Points:

1. **Graceful degradation** - Accept orders even if DB down
2. **Dead Letter Queue** - Persistent file storage as backup
3. **Circuit breaker** - Don't spam DB when it's down
4. **Automatic retry** - Replay DLQ when DB comes back
5. **No data loss** - Orders survive server restarts

---

## Q3: Market Data Service, Execution Service and Database Service interaction amongst them is critical. What are the key design considerations?

### Problem Context:

Three independent services need to coordinate without creating bottlenecks or data inconsistencies.

### Architecture Diagram:

```
┌─────────────────────────────────────────────────────────────┐
│                    ORDER FLOW ORCHESTRATION                  │
└─────────────────────────────────────────────────────────────┘

┌──────────────┐    FIX Message      ┌─────────────────────┐
│  FIX Client  │  ──────────────────> │  Order Application  │
└──────────────┘                      └──────────┬──────────┘
                                                  │
                    ┌─────────────────────────────┼─────────────────────────────┐
                    │                             │                             │
                    ↓                             ↓                             ↓
          ┌──────────────────┐        ┌──────────────────┐        ┌──────────────────┐
          │ Market Data Svc  │        │ Execution Svc    │        │ Database Svc     │
          │ (Validation)     │        │ (Order Matching) │        │ (Persistence)    │
          └────────┬─────────┘        └────────┬─────────┘        └────────┬─────────┘
                   │                           │                           │
                   └───────────────────────────┼───────────────────────────┘
                                               │
                                      ┌────────▼────────┐
                                      │  Event Stream   │
                                      │ (WebSocket/Kafka)
                                      └─────────────────┘
                                              │
                                              ↓
                                      ┌──────────────┐
                                      │   UI Display │
                                      └──────────────┘
```

### Key Design Considerations:

```java
// 1. EVENT-DRIVEN ASYNCHRONOUS ARCHITECTURE
// Don't call services synchronously - use events/queues

public enum OrderEvent {
    ORDER_RECEIVED,
    ORDER_VALIDATED,
    ORDER_MATCHED,
    EXECUTION_REPORT_RECEIVED,
    ORDER_PERSISTED,
    ERROR_OCCURRED
}

public class OrderEventBus {
    private final Map<OrderEvent, List<EventListener>> subscribers = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public void publish(OrderEvent event, Order order) {
        if (subscribers.containsKey(event)) {
            subscribers.get(event).forEach(listener -> {
                // Execute listener in thread pool to avoid blocking
                executorService.execute(() -> {
                    try {
                        listener.handle(event, order);
                    } catch (Exception e) {
                        System.err.println("[EVENT-BUS] Error in listener: " + e.getMessage());
                    }
                });
            });
        }
    }

    public void subscribe(OrderEvent event, EventListener listener) {
        subscribers.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(listener);
    }
}

// 2. SERVICE INTERFACE DEFINITIONS - Loose coupling

public interface MarketDataService {
    /**
     * Validate order against current market data
     * Should be FAST (<10ms) - synchronous call
     */
    ValidationResult validateOrder(Order order) throws MarketDataException;

    /**
     * Get current best bid/ask for symbol
     */
    Quote getQuote(String symbol);
}

public interface ExecutionService {
    /**
     * Match order against order book
     * Returns execution details (qty, price, etc)
     */
    ExecutionResult matchOrder(Order order) throws ExecutionException;

    /**
     * Update order status based on execution
     */
    void updateOrderStatus(String orderId, OrderStatus status);
}

public interface DatabaseService {
    /**
     * Async insert - fire and forget with callback
     */
    void insertOrderAsync(Order order, CompletableFuture<Void> callback);

    /**
     * Get order history
     */
    List<Order> getOrderHistory(String symbol, int limit);
}

// 3. ORCHESTRATION LAYER - Coordinates service interactions

public class OrderOrchestrator {
    private final MarketDataService marketData;
    private final ExecutionService execution;
    private final DatabaseService database;
    private final OrderEventBus eventBus;

    public void processOrder(Order order) {
        try {
            // Phase 1: VALIDATION (Fast path)
            System.out.println("[ORCHESTRATOR] Phase 1: Validating order...");
            ValidationResult validation = marketData.validateOrder(order);
            if (!validation.isValid()) {
                eventBus.publish(OrderEvent.ERROR_OCCURRED, order);
                return;
            }
            eventBus.publish(OrderEvent.ORDER_VALIDATED, order);

            // Phase 2: EXECUTION (Can be async)
            System.out.println("[ORCHESTRATOR] Phase 2: Matching order...");
            CompletableFuture<ExecutionResult> executionFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return execution.matchOrder(order);
                } catch (ExecutionException e) {
                    throw new CompletionException(e);
                }
            });

            // Phase 3: DATABASE (Async - non-blocking)
            System.out.println("[ORCHESTRATOR] Phase 3: Persisting to database...");
            CompletableFuture<Void> dbFuture = new CompletableFuture<>();
            database.insertOrderAsync(order, dbFuture);

            // Phase 4: Wait for all operations to complete
            CompletableFuture.allOf(executionFuture, dbFuture).whenComplete((result, exception) -> {
                if (exception != null) {
                    System.err.println("[ORCHESTRATOR] Processing failed: " + exception.getMessage());
                    eventBus.publish(OrderEvent.ERROR_OCCURRED, order);
                } else {
                    System.out.println("[ORCHESTRATOR] ✓ Order processing complete");
                    eventBus.publish(OrderEvent.ORDER_PERSISTED, order);
                }
            });

        } catch (Exception e) {
            System.err.println("[ORCHESTRATOR] Fatal error: " + e.getMessage());
            eventBus.publish(OrderEvent.ERROR_OCCURRED, order);
        }
    }
}

// 4. TIMEOUT & CIRCUIT BREAKER - Prevent cascading failures

public abstract class ResilientService {
    protected static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    protected static final int MAX_RETRIES = 3;

    protected <T> Optional<T> callWithTimeout(String serviceName, Callable<T> callable) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<T> future = executor.submit(callable);
            T result = future.get(DEFAULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            System.out.println("[" + serviceName + "] ✓ Call successful");
            return Optional.of(result);
        } catch (TimeoutException e) {
            System.err.println("[" + serviceName + "] ✗ TIMEOUT - took longer than " +
                             DEFAULT_TIMEOUT.getSeconds() + "s");
            future.cancel(true);
            return Optional.empty();
        } catch (Exception e) {
            System.err.println("[" + serviceName + "] ✗ Error: " + e.getMessage());
            return Optional.empty();
        } finally {
            executor.shutdownNow();
        }
    }
}

// 5. ORDERING GUARANTEES - Ensure consistent state

public class OrderStateManager {
    private static class OrderState {
        volatile OrderStatus status;
        volatile long lastUpdate;
        volatile int version; // Optimistic locking
    }

    private final Map<String, OrderState> orderStates = new ConcurrentHashMap<>();

    /**
     * Update order status with version check
     * Prevents "lost updates" when multiple services update same order
     */
    public synchronized boolean updateStatus(String orderId, OrderStatus newStatus, int expectedVersion) {
        OrderState state = orderStates.get(orderId);

        if (state == null || state.version != expectedVersion) {
            System.err.println("[STATE-MGR] ✗ Version mismatch - order was updated elsewhere");
            return false; // Optimistic lock failed
        }

        state.status = newStatus;
        state.version++;
        state.lastUpdate = System.currentTimeMillis();

        System.out.println("[STATE-MGR] ✓ Status updated to " + newStatus);
        return true;
    }
}
```

### Key Design Consider Points to Mention:

1. **Asynchronous & Event-Driven**
   - Don't make synchronous calls between services
   - Use event bus or message queue (Kafka)
   - Services publish events, others subscribe

2. **Loose Coupling**
   - Services don't know about each other's implementation
   - Depend on interfaces, not concrete classes
   - Can swap implementations (e.g., File DB → PostgreSQL)

3. **Timeouts & Circuit Breakers**
   - Market Data: <10ms (critical for validation)
   - Execution: <100ms (order matching)
   - Database: <500ms (can be relaxed with DLQ)
4. **Ordering Guarantees**
   - Market Data → Execution → Database (one-way flow)
   - Use version numbers to detect concurrent updates
   - Use optimistic locking to prevent race conditions

5. **Backup & Failover**
   - If one service fails, don't cascade
   - Use fallback values (stale data better than no data)
   - Log failures for manual recovery

6. **Monitoring & Observability**
   - Track service latency
   - Monitor queue depths (sign of bottleneck)
   - Alert on service failures

---

## Q4: Order details should get updated based on executions. What are these parameters that should be updated back on orders?

### Problem Context:

When execution happens (partial fill, full fill, cancel), which fields on the order must be updated?

### FIX Execution Report Fields to Update:

```sql
-- LAB6_SCHEMA.sql - Updated orders table structure

CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(50) PRIMARY KEY,
    cl_ord_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side CHAR(1) NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    quantity DECIMAL(15, 0) NOT NULL,

    -- EXECUTION TRACKING FIELDS (Updated on each ExecutionReport)
    cum_qty DECIMAL(15, 0) DEFAULT 0,              -- Cumulative executed qty
    leaves_qty DECIMAL(15, 0),                     -- Remaining qty to execute
    avg_px DECIMAL(15, 4) DEFAULT 0,               -- Average execution price

    -- STATUS TRACKING
    ord_status VARCHAR(20) DEFAULT 'NEW',          -- NEW, PENDING_NEW, ACCEPTED, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED
    exec_type VARCHAR(20),                         -- NEW, PARTIAL_FILL, FILL, CANCELLED, REJECTED

    -- TIMESTAMPS
    created_at TIMESTAMPTZ DEFAULT NOW(),
    last_exec_time TIMESTAMPTZ,                    -- When last execution happened
    last_exec_id VARCHAR(50),                      -- Link to last execution

    -- REJECTION/ERROR INFO
    reject_reason VARCHAR(255),
    reject_code VARCHAR(50),

    -- AUDIT
    status_updated_at TIMESTAMPTZ,
    updated_by VARCHAR(50),

    timestamp TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS executions (
    exec_id VARCHAR(50) PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL,
    exec_qty INT NOT NULL,
    exec_price DECIMAL(15, 2) NOT NULL,
    exec_time TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
);
```

### Java Code - Update Logic on Execution:

```java
// OrderExecutionHandler.java

public class OrderExecutionHandler {

    /**
     * Called when ExecutionReport is received from exchange/execution engine
     *
     * Scenario 1: Partial Fill
     *   - cum_qty = 50 (of 100 order)
     *   - leaves_qty = 50
     *   - avg_px = weighted average
     *   - ord_status = PARTIALLY_FILLED
     *
     * Scenario 2: Full Fill
     *   - cum_qty = 100 (entire order)
     *   - leaves_qty = 0
     *   - avg_px = final average
     *   - ord_status = FILLED
     *
     * Scenario 3: Rejection
     *   - cum_qty = 0 (no executions)
     *   - leaves_qty = original order qty
     *   - ord_status = REJECTED
     *   - reject_reason = "Insufficient credit limit"
     */
    public static void processExecutionReport(ExecutionReport execReport) {
        try {
            String orderId = execReport.get(new OrderID()).getValue();
            int execQty = (int) execReport.get(new LastQty()).getValue();
            double execPrice = execReport.get(new LastPx()).getValue();
            char execType = execReport.get(new ExecType()).getValue();

            // Get current order state
            Order currentOrder = DatabaseManager.getOrder(orderId);

            // Calculate new cumulative values
            double newCumQty = currentOrder.getCumQty() + execQty;
            double newLeavesQty = currentOrder.getQuantity() - newCumQty;

            // Calculate weighted average price
            double newAvgPx = calculateWeightedAverage(
                currentOrder.getCumQty(),
                currentOrder.getAvgPx(),
                execQty,
                execPrice
            );

            // Determine new order status
            String newStatus = determineOrderStatus(execType, newCumQty, currentOrder.getQuantity());

            // Build update SQL
            String updateSQL = "UPDATE orders SET " +
                "cum_qty = ?, " +
                "leaves_qty = ?, " +
                "avg_px = ?, " +
                "ord_status = ?, " +
                "exec_type = ?, " +
                "last_exec_time = NOW(), " +
                "last_exec_id = ?, " +
                "status_updated_at = NOW() " +
                "WHERE order_id = ?";

            try (Connection conn = DriverManager.getConnection(DB_URL);
                 PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

                pstmt.setDouble(1, newCumQty);           // cum_qty
                pstmt.setDouble(2, newLeavesQty);        // leaves_qty
                pstmt.setDouble(3, newAvgPx);            // avg_px
                pstmt.setString(4, newStatus);           // ord_status
                pstmt.setString(5, String.valueOf(execType)); // exec_type
                pstmt.setString(6, execReport.get(new ExecID()).getValue()); // last_exec_id
                pstmt.setString(7, orderId);             // where clause

                int rowsUpdated = pstmt.executeUpdate();
                if (rowsUpdated > 0) {
                    System.out.println("[EXECUTION] ✓ Order updated: " + orderId +
                                     " | CumQty:" + newCumQty +
                                     " | Status:" + newStatus +
                                     " | AvgPx:" + newAvgPx);
                }
            }

            // Log execution details for audit
            logExecutionDetails(orderId, execReport, newCumQty, newAvgPx, newStatus);

        } catch (FieldNotFound e) {
            System.err.println("[EXECUTION] Error processing report: " + e.getMessage());
        }
    }

    /**
     * Calculate weighted average price
     * Example:
     *   - First exec: 50 qty @ $100 = $5,000
     *   - Second exec: 50 qty @ $102 = $5,100
     *   - Weighted avg = $10,100 / 100 = $101
     */
    private static double calculateWeightedAverage(
        double prevQty,
        double prevAvgPx,
        double newQty,
        double newPrice) {

        if (prevQty + newQty == 0) return 0;

        double totalCost = (prevQty * prevAvgPx) + (newQty * newPrice);
        return totalCost / (prevQty + newQty);
    }

    private static String determineOrderStatus(char execType, double cumQty, double totalQty) {
        switch (execType) {
            case '2': // FILL
                return (cumQty >= totalQty) ? "FILLED" : "PARTIALLY_FILLED";
            case '3': // NEW
                return "NEW";
            case '4': // CANCELLED
                return "CANCELLED";
            case '8': // REJECTED
                return "REJECTED";
            case '1': // PARTIAL_FILL
                return "PARTIALLY_FILLED";
            default:
                return "UNKNOWN";
        }
    }
}
```

### Parameters to Update Summary Table:

| Parameter           | Type      | Updated When      | Notes                                        |
| ------------------- | --------- | ----------------- | -------------------------------------------- |
| `cum_qty`           | DECIMAL   | Any execution     | Sum of all executed quantities               |
| `leaves_qty`        | DECIMAL   | Any execution     | Original qty - cum_qty                       |
| `avg_px`            | DECIMAL   | Any execution     | Weighted average execution price             |
| `ord_status`        | VARCHAR   | Any execution     | NEW → PARTIALLY_FILLED → FILLED              |
| `exec_type`         | VARCHAR   | Any execution     | PARTIAL_FILL, FILL, CANCELLED, REJECTED      |
| `last_exec_time`    | TIMESTAMP | Any execution     | When this execution happened                 |
| `last_exec_id`      | VARCHAR   | Any execution     | Reference to execution record                |
| `status_updated_at` | TIMESTAMP | Any status change | For audit trail                              |
| `reject_reason`     | VARCHAR   | On rejection      | "Insufficient credit", "Invalid symbol", etc |
| `reject_code`       | VARCHAR   | On rejection      | System error code                            |

---

## Q5: Think of Race conditions and your thoughts around managing the risks

### Race Conditions in Trading Systems:

#### Problem Scenarios:

```
SCENARIO 1: Lost Update

Thread A (Execution 1)        Thread B (Execution 2)
   ↓                                  ↓
Read order cum_qty=0         Read order cum_qty=0
   ↓                                  ↓
Calculate cum_qty=50          Calculate cum_qty=50
   ↓                                  ↓
Write cum_qty=50             Write cum_qty=50
                                      ↓
   RESULT: Should be 100, but shows 50! ✗


SCENARIO 2: Dirty Read (Stale Data)

Order Processing Thread           Database Persister Thread
   ↓                                    ↓
Read order status = NEW
                                  Update to PARTIALLY_FILLED
   ↓
Broadcast old status (NEW)   ← UI gets WRONG info ✗


SCENARIO 3: Phantom Read (Buffer Overflow)

Execution Service                    Database Service
   ↓                                       ↓
Add order to queue (item 10,000)
                                  Dequeue batch slowly
   ↓                                       ↓
Add more orders rapidly
                                  Queue fills up completely
   Block new orders ✗


SCENARIO 4: Double-Processing

Order received, queued to DB
   • DB Persister thread crashes mid-insert
   • Process restarts, replays Dead Letter Queue
   • Same order inserted twice ✗
```

### Solutions:

```java
// SOLUTION 1: OPTIMISTIC LOCKING - Prevent lost updates

public class OrderOptimisticLock {

    /**
     * Track version of each order
     * If version changes between read and write, FAIL and RETRY
     */
    public static boolean updateOrderOnExecution(String orderId, ExecutionData exec) {
        // Step 1: Read current version
        Order currentOrder = DatabaseManager.getOrder(orderId);
        int currentVersion = currentOrder.getVersion();  // e.g., version = 3

        // Step 2: Calculate new values
        double newCumQty = currentOrder.getCumQty() + exec.quantity;
        double newAvgPx = calculateWeightedAverage(...);

        // Step 3: UPDATE with WHERE version check
        String updateSQL =
            "UPDATE orders SET " +
            "cum_qty = ?, avg_px = ?, version = version + 1 " +
            "WHERE order_id = ? AND version = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(updateSQL)) {

            pstmt.setDouble(1, newCumQty);
            pstmt.setDouble(2, newAvgPx);
            pstmt.setString(3, orderId);
            pstmt.setInt(4, currentVersion);  // Check version matches!

            int rowsUpdated = pstmt.executeUpdate();

            if (rowsUpdated == 0) {
                // Another thread updated this order - RETRY
                System.out.println("[LOCK] Version mismatch - retrying...");
                return false; // Signal retry needed
            }

            System.out.println("[LOCK] ✓ Update successful (v" + currentVersion + " → " + (currentVersion+1) + ")");
            return true;
        }
    }
}

// SOLUTION 2: PESSIMISTIC LOCKING - Lock before update

public class OrderPessimisticLock {

    /**
     * LOCK row BEFORE updating - ensure exclusive access
     */
    public static synchronized void updateOrderSafely(String orderId, ExecutionData exec) {
        // Database-level row lock
        String lockSQL = "SELECT * FROM orders WHERE order_id = ? FOR UPDATE";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(lockSQL)) {

            pstmt.setString(1, orderId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Row is now LOCKED - no other transaction can update

                double newCumQty = rs.getDouble("cum_qty") + exec.quantity;

                String updateSQL = "UPDATE orders SET cum_qty = ? WHERE order_id = ?";
                try (PreparedStatement updatePstmt = conn.prepareStatement(updateSQL)) {
                    updatePstmt.setDouble(1, newCumQty);
                    updatePstmt.setString(2, orderId);
                    updatePstmt.executeUpdate();

                    // Commit releases lock
                    conn.commit();
                }
            }
        } catch (SQLException e) {
            System.err.println("[PESSIMISTIC] Lock failed: " + e.getMessage());
        }
        // Lock automatically released when connection closes
    }
}

// SOLUTION 3: MESSAGE IDEMPOTENCY - Prevent double-processing

public class IdempotentProcessor {

    private static final Set<String> processedExecutions = Collections.newSetFromMap(
        new ConcurrentHashMap<>()
    );

    /**
     * Check if execution already processed
     * Use execution ID as idempotency key
     */
    public static boolean shouldProcess(String execId) {
        if (processedExecutions.contains(execId)) {
            System.out.println("[IDEMPOTENT] Skipping duplicate execution: " + execId);
            return false;
        }
        processedExecutions.add(execId);
        return true;
    }

    // Better: Store in database
    public static boolean isExecutionAlreadyProcessed(String execId) {
        String checkSQL = "SELECT COUNT(*) FROM executions WHERE exec_id = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(checkSQL)) {

            pstmt.setString(1, execId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("[IDEMPOTENT] Check failed: " + e.getMessage());
        }
        return false;
    }
}

// SOLUTION 4: THREAD-SAFE COLLECTIONS - Prevent buffer overflow

public class ThreadSafeOrderQueue {

    // Use blocking queue with bounded capacity
    private final BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>(10000);

    public void addOrder(Order order) throws InterruptedException {
        // This will BLOCK if queue is full
        // Prevents memory overflow
        orderQueue.put(order);
        System.out.println("[QUEUE] Order added. Size: " + orderQueue.size());
    }

    // Alternative: Non-blocking with rejection
    public boolean tryAddOrder(Order order) {
        boolean added = orderQueue.offer(order, 100, TimeUnit.MILLISECONDS);
        if (!added) {
            System.err.println("[QUEUE] Queue full - order rejected");
            // Send REJECT message to client
        }
        return added;
    }
}

// SOLUTION 5: DATABASE CONSTRAINTS - Let database enforce rules

public class ConstraintBasedSafety {

    /*
    PostgreSQL constraints prevent invalid states:

    1. PRIMARY KEY (order_id)
       - Prevents duplicate order IDs

    2. CHECK (cum_qty <= quantity)
       - Cumulative qty cannot exceed order qty

    3. CHECK (leaves_qty = quantity - cum_qty)
       - Automatic consistency check

    4. CHECK (avg_px >= 0)
       - No negative prices

    5. FOREIGN KEY (last_exec_id REFERENCES executions)
       - Cannot reference non-existent execution

    6. UNIQUE (exec_id)
       - No duplicate execution IDs
    */

    static String CREATE_TABLE_SQL = """
        CREATE TABLE orders (
            order_id VARCHAR(50) PRIMARY KEY,
            quantity DECIMAL(15, 0) NOT NULL,
            cum_qty DECIMAL(15, 0) DEFAULT 0,
            leaves_qty DECIMAL(15, 0) GENERATED ALWAYS AS (quantity - cum_qty) STORED,
            avg_px DECIMAL(15, 4) DEFAULT 0,

            -- Constraints
            CONSTRAINT check_cum_qty CHECK (cum_qty <= quantity),
            CONSTRAINT check_leaves_qty CHECK (leaves_qty >= 0),
            CONSTRAINT check_avg_px CHECK (avg_px >= 0)
        );

        CREATE UNIQUE INDEX idx_order_id ON orders(order_id);
    """;
}

// SOLUTION 6: TRANSACTION ISOLATION LEVELS

public class TransactionIsolation {

    /*
    Choose appropriate isolation level:

    SERIALIZABLE (Highest safety, lowest performance)
    └─ No dirty reads, no lost updates, but very slow

    REPEATABLE_READ (Default in PostgreSQL)
    └─ Safe for orders - prevents most race conditions

    READ_COMMITTED (Fast but risky)
    └─ Can see uncommitted changes

    READ_UNCOMMITTED (Fastest but most risky)
    └─ Use only for non-critical reads
    */

    public static void updateOrderWithIsolation(String orderId, ExecutionData exec) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {

            // Set isolation level
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            conn.setAutoCommit(false);

            // Read-modify-write in single transaction
            Order currentOrder = readOrder(conn, orderId);
            Order updatedOrder = currentOrder.withNewExecution(exec);
            updateOrder(conn, updatedOrder);

            // Commit atomically
            conn.commit();

            System.out.println("[ISOLATION] ✓ Transaction committed safely");
        } catch (SQLException e) {
            // Retry logic
            System.err.println("[ISOLATION] ✗ Conflict - retrying: " + e.getMessage());
        }
    }
}
```

### Race Condition Management Summary:

| Race Condition     | Solution              | Pros                    | Cons                   |
| ------------------ | --------------------- | ----------------------- | ---------------------- |
| Lost Updates       | Optimistic Locking    | Fast, scales well       | Need retry logic       |
| Dirty Reads        | Pessimistic Locking   | Guarantees safety       | Can deadlock, slow     |
| Double-Processing  | Idempotency Keys      | Works even with retries | Need to track IDs      |
| Buffer Overflow    | Bounded Queues        | Prevents OOM            | Backpressure needed    |
| Invalid States     | DB Constraints        | Enforced at source      | Need good schema       |
| Dirty Isolation    | Transaction Isolation | Database handles it     | Performance trade-off  |
| Concurrent Updates | Row Versioning        | Detect conflicts        | Application complexity |

### Recommendation for Your System:

```java
// Recommended approach for trading order system:

public class HybridRaceConditionHandler {

    // Use this layered approach:

    // Layer 1: Database constraints (prevents impossible states)
    // Layer 2: Optimistic locking (fast for most cases)
    // Layer 3: Retry mechanism (handle conflicts)
    // Layer 4: Dead letter queue (final safety net)
    // Layer 5: Monitoring & alerting (catch problems early)
}
```

---

## Implementation Priority

**For your presentation tomorrow, focus on:**

1. **Q1 (System Ready)** - Show FIX version validation code
2. **Q2 (DLQ)** - Explain Dead Letter Queue pattern + show file-based backup
3. **Q3 (Services)** - Draw architecture diagram with event bus
4. **Q4 (Order Updates)** - Show SQL table with cum_qty, leaves_qty, avg_px fields
5. **Q5 (Race Conditions)** - Focus on optimistic locking + database constraints

These address the professor's concerns about production-readiness and fault tolerance!
