ill# MATCHING ENGINE & ORDER BOOK IMPLEMENTATION BRIEF

## 🎯 PROJECT CONTEXT

**Trading Order Management System** - A financial trading platform that:

1. Receives orders via FIX Protocol (from MiniFix clients)
2. Stores orders in PostgreSQL database
3. **[NEW] Matches orders against order book** ← YOU ARE HERE
4. Generates trade executions
5. Updates order status (NEW → PARTIALLY_FILLED → FILLED)
6. Broadcasts real-time updates to Angular UI via WebSocket
7. Calculates options prices (future requirement)

---

## 📊 CURRENT SYSTEM STATUS

### ✅ Already Implemented:

- **FIX Protocol Integration** (QuickFIX/J 2.3.1)
  - Order ingestion from MiniFix clients
  - FIX message parsing (NewOrderSingle, CancelOrder)
  - ExecutionReport generation
- **Order Model & Validation**
  - Order POJO with fields: orderId, clOrdId, symbol, side, price, quantity, status, timestamp
  - Basic validation (positive price/quantity)
  - Security master lookup (8 symbols: GOOG, MSFT, IBM, AAPL, AMZN, TSLA, SPY, QQQ)
- **Database Layer**
  - PostgreSQL with orders table
  - OrderPersister (async database writer with BlockingQueue)
  - Graceful shutdown with queue draining
- **WebSocket Broadcasting**
  - OrderBroadcaster (sends JSON orders to Angular UI)
  - Real-time order display in order-grid component
- **Entry Point Integration**
  - AppLauncher.java orchestrates all startup
  - Health checks before accepting orders

### 🔄 Current Data Flow:

```
MiniFix Client
  ↓ (FIX NewOrderSingle)
OrderApplication.processNewOrder()
  ↓
1. Extract & validate fields
2. Load reference data (security master)
3. Send FIX ExecutionReport (Status=NEW)
4. Queue to database (async)
5. Broadcast to UI via WebSocket
  ↓
[MISSING: Matching logic, order book, executions]
```

### ❌ Missing Features:

- **Order Book**: No bid/ask book per symbol
- **Matching Engine**: No matching logic
- **Trade Generation**: No trade creation
- **Execution Events**: No TradeExecuted events
- **Order Updates**: Orders never go from NEW → PARTIALLY_FILLED → FILLED

---

## 📋 DELIVERABLES TO IMPLEMENT

### **Deliverable 1: Order Book Manager (G2-M1)**

**File**: `stocker/cmt/src/main/java/com/stocker/OrderBook.java`

**Responsibility**: Manage bid/ask queues for each symbol using price-time priority

**Data Structures**:

```
OrderBook (one per symbol)
├── Bids (TreeSet, Price DESC)
│   └── Order objects ordered by: Price DESC, then Timestamp ASC
├── Asks (TreeSet, Price ASC)
│   └── Order objects ordered by: Price ASC, then Timestamp ASC
└── Statistics (volume, vwap, etc.)
```

**Core Methods**:

1. **`addOrder(Order order)`**
   - Add order to appropriate side (BID or ASK)
   - Maintain price-time priority in TreeSet
   - Return true/false for success

2. **`removeOrder(String orderId)`**
   - Remove order from bid or ask side
   - Handle case if order not found

3. **`getTopOfBook()`**
   - Return best bid and ask (for UI/market data)
   - Return `TopOfBook{ bestBid, bestAsk, bidQty, askQty }`

4. **`getOrdersByStatus(String status)`**
   - Return all orders with status (NEW, PARTIALLY_FILLED, etc.)
   - Used for cache invalidation

5. **`updateOrderStatus(String orderId, String newStatus)`**
   - Update order status on this symbol
   - Trigger event emission

**Concurrency Strategy**:

- Use `ConcurrentHashMap<String, OrderBook>` at MatchingEngine level
- Within OrderBook, use `ReadWriteLock` or `synchronized` blocks
- Rationale: Symbol-level locking (each symbol has its own book, reduces contention)

**Key Design Points**:

- TreeSet with custom Comparator for price-time priority
- ThreadSafe (reading top-of-book while updates happen)
- O(log n) insertion/deletion
- O(1) top-of-book lookup

---

### **Deliverable 2: Matching Engine (G2-M2)**

**File**: `stocker/cmt/src/main/java/com/stocker/MatchingEngine.java`

**Responsibility**: Match orders against order book and generate executions

**Core Concept**:

```
When a BUY order arrives:
  1. Check order book for ASK (sell) orders
  2. Match against lowest ASK prices first (best price priority)
  3. Within same price, match against oldest ask first (time priority)
  4. Generate Execution objects
  5. Update order status and leaves_qty
  6. Emit TradeExecuted events
  7. Remove fully-filled orders from book

When a SELL order arrives:
  1. Check order book for BID (buy) orders
  2. Match against highest BID prices first
  3. Within same price, match against oldest bid first
  4. [Same as above]
```

**Data Model - Execution** (New class needed):

```java
public class Execution {
    private String execId;              // Unique execution ID
    private String buyOrderId;          // Order that bought
    private String sellOrderId;         // Order that sold
    private String symbol;
    private int execQty;                // Quantity matched
    private double execPrice;           // Price matched
    private long timestamp;

    // Constructor, getters, setters
}
```

**Data Model - Trade** (New class needed):

```java
public class Trade {
    private String tradeId;             // Unique trade reference
    private List<Execution> executions; // 1+ executions for this trade
    private double totalQty;
    private double vwap;                // Volume-weighted average price
    private String symbol;
    private long tradeTime;

    // Constructor, getters, setters
}
```

**Core Methods**:

1. **`List<Execution> matchOrder(Order incomingOrder)`**
   - Main entry point called from OrderApplication
   - Returns list of executions generated (0+ executions)
   - Modifies order book in place
   - Algorithm:

     ```
     IF incoming is BUY:
       oppositeBook = orderBook[symbol].asks
       bestPrices = Sort ASK prices ASC (lowest first)
     ELSE if incoming is SELL:
       oppositeBook = orderBook[symbol].bids
       bestPrices = Sort BID prices DESC (highest first)

     remainingQty = incoming.quantity
     FOR EACH order in oppositeBook ordered by bestPrices:
       IF prices don't cross (BUY price < SELL price):
         BREAK  // No more matches

       matchQty = min(remainingQty, order.leavesQty)
       execution = new Execution(...)
       executions.add(execution)

       order.cumQty += matchQty
       order.leavesQty -= matchQty
       order.avgPx = calculateWeightedAvg(...)

       remainingQty -= matchQty

       IF order.leavesQty == 0:
         order.status = "FILLED"
         orderBook[symbol].removeOrder(order.orderId)
       ELSE:
         order.status = "PARTIALLY_FILLED"

     // Update incoming order
     incoming.cumQty = incoming.quantity - remainingQty
     incoming.leavesQty = remainingQty
     IF remainingQty == 0:
       incoming.status = "FILLED"
       orderBook[symbol].removeOrder(incoming.orderId)
     ELSE:
       incoming.status = "PARTIALLY_FILLED"
       orderBook[symbol].addOrder(incoming)  // Add to book

     RETURN executions
     ```

2. **`void cancelOrder(String orderId, String symbol)`**
   - Remove order from book
   - Update order status to CANCELLED
   - Cannot cancel if already FILLED

3. **`Trade generateTrade(List<Execution> executions)`**
   - Create Trade from executions
   - Calculate VWAP: `sum(execQty * execPrice) / sum(execQty)`
   - Generate unique Trade ID
   - Return Trade object

4. **`double calculateWeightedAvgPrice(Order order, int matchQty, double matchPrice)`**
   - Used to compute avg_px after each execution
   - Formula: `(cumQty * prevAvgPx + matchQty * matchPrice) / (cumQty + matchQty)`

**Integration Points**:

1. **Called from OrderApplication.processNewOrder()**:

   ```java
   List<Execution> executions = matchingEngine.matchOrder(order);
   if (!executions.isEmpty()) {
       eventBus.publish(OrderEvent.ORDER_EXECUTED, order, executions);
   }
   ```

2. **Emits Events**:
   - `OrderEvent.ORDER_EXECUTED` → with list of Executions
   - `OrderEvent.ORDER_FILLED` → when order fully matched
   - `OrderEvent.TRADE_GENERATED` → when trade generated

3. **Update Order Fields**:
   - `order.cumQty` ← Cumulative executed quantity
   - `order.leavesQty` ← Remaining quantity
   - `order.avgPx` ← Weighted average execution price
   - `order.status` ← NEW, PARTIALLY_FILLED, FILLED
   - `order.lastExecId` ← Reference to last execution
   - `order.lastExecTime` ← Timestamp of last execution

**Concurrency Considerations**:

- **Lock Strategy**: Lock symbol's OrderBook during matching
  ```java
  OrderBook book = orderBooks.get(symbol);
  synchronized(book) {
      List<Execution> execs = matchOrdersInternal(order, book);
  }
  ```
- **Why**: Prevent concurrent matches on same symbol
- **Trade-off**: Serializes all trades per symbol (acceptable for single exchange)

---

## 🗂️ FILE STRUCTURE

```
stocker/cmt/
├── src/main/java/com/stocker/
│   ├── AppLauncher.java ← Already exists (update to init MatchingEngine)
│   ├── OrderApplication.java ← Already exists (add matching call)
│   ├── Order.java ← Already exists (may add new fields)
│   ├── OrderBook.java ← CREATE NEW
│   ├── MatchingEngine.java ← CREATE NEW
│   ├── Execution.java ← CREATE NEW
│   ├── Trade.java ← CREATE NEW
│   ├── OrderEventBus.java ← CREATE NEW (for event publishing)
│   └── [existing files...]
│
└── LAB6_SCHEMA.sql ← Already exists (update orders table)
```

---

## 🗄️ DATABASE SCHEMA UPDATES

**File**: `stocker/cmt/LAB6_SCHEMA.sql`

**Update orders table**:

```sql
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cum_qty DECIMAL(15, 0) DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS leaves_qty DECIMAL(15, 0);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS avg_px DECIMAL(15, 4) DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS exec_type VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS last_exec_id VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS last_exec_time TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS executions (
    exec_id VARCHAR(50) PRIMARY KEY,
    order_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side CHAR(1) NOT NULL,
    exec_qty INT NOT NULL,
    exec_price DECIMAL(15, 2) NOT NULL,
    exec_time TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
);

CREATE TABLE IF NOT EXISTS trades (
    trade_id VARCHAR(50) PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    buy_order_id VARCHAR(50),
    sell_order_id VARCHAR(50),
    exec_qty INT NOT NULL,
    exec_price DECIMAL(15, 2) NOT NULL,
    vwap DECIMAL(15, 4),
    trade_time TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_buy_order FOREIGN KEY (buy_order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_sell_order FOREIGN KEY (sell_order_id) REFERENCES orders(order_id)
);

CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades(symbol);
CREATE INDEX IF NOT EXISTS idx_trades_time ON trades(trade_time DESC);
```

---

## 🔌 INTEGRATION WITH EXISTING CODE

### 1. Modify AppLauncher.java

**After**: `OrderApplication application = new OrderApplication(...)`

**Add**:

```java
// Initialize Matching Engine
MatchingEngine matchingEngine = new MatchingEngine();
System.out.println("[STARTUP] ✓ Matching Engine initialized");

// Pass to OrderApplication
OrderApplication application = new OrderApplication(broadcaster, dbQueue, matchingEngine);
```

### 2. Modify OrderApplication.java

**In**: `processNewOrder()` method after validation

**Change**:

```java
// BEFORE:
acceptOrder(message, sessionId);

// AFTER:
List<Execution> executions = matchingEngine.matchOrder(order);

if (executions.isEmpty()) {
    // No match - add to book as pending
    acceptOrder(message, sessionId);
} else {
    // Matched - generate ExecutionReport with fills
    int totalFilled = executions.stream()
        .mapToInt(Execution::getExecQty)
        .sum();

    if (totalFilled == order.getQuantity()) {
        // Full fill
        sendExecutionReport(message, sessionId, order, "FILLED");
    } else {
        // Partial fill
        sendExecutionReport(message, sessionId, order, "PARTIALLY_FILLED");
    }
}

// Persist to database (already in place)
dbQueue.offer(order);
```

### 3. Create EventBus for Order Updates

**New file**: `OrderEventBus.java`

```java
public class OrderEventBus {
    enum OrderEvent {
        ORDER_EXECUTED, ORDER_FILLED, TRADE_GENERATED,
        EXECUTION_PERSISTED
    }

    private final Map<OrderEvent, List<Consumer<Order>>> subscribers = new ConcurrentHashMap<>();

    public void subscribe(OrderEvent event, Consumer<Order> listener) { ... }
    public void publish(OrderEvent event, Order order) { ... }
}
```

---

## 🧪 TEST SCENARIOS

### Test 1: Simple Match (Full Fill)

```
Action: Send BUY order (100 @ $100)
Expected: Order added to BID book
Action: Send SELL order (100 @ $99)
Expected:
  - BUY order: status=FILLED, cumQty=100, leavesQty=0
  - SELL order: status=FILLED, cumQty=100, leavesQty=0
  - Execution generated: qty=100, price=$100 (best bid wins)
  - Trade generated
```

### Test 2: Partial Fill

```
Action: Send BUY order (100 @ $100)
Action: Send SELL order (60 @ $99)
Expected:
  - SELL order: status=FILLED, cumQty=60
  - BUY order: status=PARTIALLY_FILLED, cumQty=60, leavesQty=40
  - BUY order remains on book for 40 qty
```

### Test 3: Price Priority

```
Action: BUY order (50 @ $102) added to book
Action: BUY order (50 @ $100) added to book
Action: SELL order (50 @ $101)
Expected:
  - Matches against $102 bid first (better price for seller)
  - $100 bid remains on book
```

### Test 4: Time Priority (Same Price)

```
Action: BUY order A (50 @ $100, timestamp=T1)
Action: BUY order B (50 @ $100, timestamp=T2, T2 > T1)
Action: SELL order (50 @ $99)
Expected:
  - Matches against order A (older, came first)
  - Order B remains on book
```

### Test 5: Cancel Order

```
Action: BUY order (100 @ $100)
Action: Cancel order
Expected:
  - Order removed from book
  - Status set to CANCELLED
  - Cannot cancel after FILLED
```

---

## 📊 DATA STRUCTURES DEEP DIVE

### OrderBook Structure:

```java
class OrderBook {
    private final String symbol;
    private final NavigableSet<Order> bids;  // Price DESC, Time ASC
    private final NavigableSet<Order> asks;  // Price ASC, Time ASC

    // Custom comparator ensures price-time priority
    private static final Comparator<Order> BID_COMPARATOR =
        Comparator.comparingDouble(Order::getPrice).reversed()
                  .thenComparingLong(Order::getTimestamp);

    // Constructor
    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.bids = Collections.synchronizedNavigableSet(
            new TreeSet<>(BID_COMPARATOR)
        );
        this.asks = Collections.synchronizedNavigableSet(
            new TreeSet<>(ASK_COMPARATOR)
        );
    }
}
```

### MatchingEngine Structure:

```java
class MatchingEngine {
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final ExecutionIdGenerator execIdGen = new ExecutionIdGenerator();
    private final TradeIdGenerator tradeIdGen = new TradeIdGenerator();

    // Called when new symbol order arrives
    private OrderBook getOrCreateBook(String symbol) {
        return orderBooks.computeIfAbsent(symbol, s -> new OrderBook(s));
    }
}
```

---

## 🔢 ID GENERATION STRATEGY

**Execution ID**: `EXEC_<SYMBOL>_<TIMESTAMP>_<SEQ>`

- Example: `EXEC_AAPL_1711003800000_001`
- Rationale: Sortable by symbol and time, unique sequence per symbol

**Trade ID**: `TRADE_<TIMESTAMP>_<SEQ>`

- Example: `TRADE_1711003800000_001`
- Rationale: Sortable by time, globally unique

```java
class ExecutionIdGenerator {
    private final AtomicLong sequence = new AtomicLong(0);

    public String generateId(String symbol) {
        long seq = sequence.incrementAndGet();
        long timestamp = System.currentTimeMillis();
        return String.format("EXEC_%s_%d_%03d", symbol, timestamp, seq % 1000);
    }
}
```

---

## 📈 PERFORMANCE TARGETS

| Operation                | Target | Implementation            |
| ------------------------ | ------ | ------------------------- |
| Add order to book        | <1 ms  | TreeSet insert O(log n)   |
| Find match               | <5 ms  | Linear scan opposite side |
| Remove order             | <1 ms  | TreeSet delete O(log n)   |
| Full flow (match + exec) | <10 ms | Optimized tree operations |

**For 500K orders**: Should process in ~5-10 seconds total (async writes ok)

---

## 🚨 ERROR HANDLING

**Cases to handle**:

1. **Symbol not in security master**: Reject order
2. **Quantity exceeds lot size**: Reject order
3. **Price outside tick rules**: Reject order (not critical for MVP)
4. **Cancel non-existent order**: Return error message
5. **Cancel already-filled order**: Return error message
6. **Duplicate order ID**: Handle idempotently

**Implementation**: Throw custom exceptions caught in OrderApplication

---

## 📝 TESTING CHECKLIST

Before submitting:

- [ ] OrderBook: Add/remove/search operations work
- [ ] Price-time priority correct for bids and asks
- [ ] Matching: Full fill scenario works
- [ ] Matching: Partial fill scenario works
- [ ] Matching: Price priority respected
- [ ] Matching: Time priority respected
- [ ] Matching: Orders remain on book if partially filled
- [ ] Cancel: Order removed from book
- [ ] Cancel: Cannot cancel filled orders
- [ ] Execution: IDs unique and sortable
- [ ] Trade: VWAP calculated correctly
- [ ] Database: Schema created (orders, executions, trades tables)
- [ ] Integration: OrderApplication calls MatchingEngine
- [ ] Events: TradeExecuted events emitted
- [ ] UI: Updates display cum_qty, leaves_qty, avg_px fields
- [ ] Load test: 1000+ orders/second throughput

---

## 🎓 KEY CONCEPTS

1. **Price-Time Priority**
   - Price priority: Better price matched first
   - Time priority: Older orders matched first within same price
   - Essential for fairness in matching

2. **Leaves Quantity**
   - `leaves_qty = order.quantity - cum_qty`
   - Tells you how much of order is still waiting
   - Updated after each partial execution

3. **Weighted Average Price**
   - After each execution, recalculate avg execution price
   - Used for P&L calculations
   - Formula: `(cumQty * oldAvg + newQty * newPrice) / (cumQty + newQty)`

4. **Idempotency**
   - If same order arrives twice, process once
   - Use order ID as idempotency key
   - Store processed order IDs in memory or database

5. **Concurrency**
   - Lock per symbol to avoid race conditions
   - Different symbols can match in parallel
   - Use `synchronized` or `ReentrantReadWriteLock`

---

## 📚 REFERENCE IMPLEMENTATIONS

For comparator and TreeSet usage, consult:

- Java TreeSet documentation
- Comparator.comparingDouble() / reversed()
- NavigableSet methods (first(), last(), pollFirst())

---

## 🔗 RELATED DELIVERABLES

- **G2-M3**: Trade Generator (uses execution list)
- **G2-M4**: Trade Persistence (insert trade to DB)
- **G2-M5**: Trade Event Streaming (publish TradeExecuted to WebSocket)
- **G2-M6**: Load Testing (stress test matching engine)

---

## ✅ COMPLETION CRITERIA

System is considered complete when:

1. ✅ OrderBook manages bids/asks with price-time priority
2. ✅ MatchingEngine matches incoming orders correctly
3. ✅ Executions generated with correct qty/price
4. ✅ Order status updates (NEW → PARTIALLY_FILLED → FILLED)
5. ✅ Trades created with VWAP calculation
6. ✅ Database schema includes cum_qty, leaves_qty, avg_px
7. ✅ UI displays updated order fields
8. ✅ All test scenarios pass
9. ✅ No race conditions with concurrent orders
10. ✅ Performance targets achieved (<10 ms per match)

---

**You are now ready to implement G2-M1 and G2-M2!** 🚀

For questions about the existing codebase, refer to:

- OrderApplication.java (FIX message handling)
- Order.java (order model)
- DatabaseManager.java (persistence patterns)
- OrderBroadcaster.java (websocket patterns)
