-- ============================================================
-- LAB 7: Matching Engine & Order Book Schema Migration
-- Capital Market Simulator - PostgreSQL Schema Updates
-- Run: psql -U postgres -d trading_system -f add_matching_engine_fields.sql
-- ============================================================

-- ============================================================
-- SECTION 1: Add Matching Engine Fields to Orders Table
-- ============================================================
ALTER TABLE orders ADD COLUMN IF NOT EXISTS cum_qty DECIMAL(15, 0) DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS leaves_qty DECIMAL(15, 0);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS avg_px DECIMAL(15, 4) DEFAULT 0;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS exec_type VARCHAR(20);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS last_exec_id VARCHAR(50);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS last_exec_time TIMESTAMPTZ;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS ord_status VARCHAR(20) DEFAULT 'NEW';

-- ============================================================
-- SECTION 2: Update Executions Table for Matching Engine
-- Add opposite_order_id to track both sides of a trade
-- ============================================================
ALTER TABLE executions ADD COLUMN IF NOT EXISTS opposite_order_id VARCHAR(50);
ALTER TABLE executions ADD COLUMN IF NOT EXISTS side CHAR(1);

-- ============================================================
-- SECTION 3: Create Trades Table
-- Aggregates multiple executions into a single trade
-- ============================================================
CREATE TABLE IF NOT EXISTS trades (
    trade_id VARCHAR(50) PRIMARY KEY,
    symbol VARCHAR(20) NOT NULL,
    buy_order_id VARCHAR(50),
    sell_order_id VARCHAR(50),
    total_qty DECIMAL(15, 0) NOT NULL,
    vwap DECIMAL(15, 4),
    trade_time TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT fk_buy_order FOREIGN KEY (buy_order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_sell_order FOREIGN KEY (sell_order_id) REFERENCES orders(order_id)
);

-- ============================================================
-- SECTION 4: Add Indexes for Matching Engine Performance
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_orders_status_symbol ON orders(status, symbol);
CREATE INDEX IF NOT EXISTS idx_orders_leaves_qty ON orders(leaves_qty) WHERE leaves_qty > 0;
CREATE INDEX IF NOT EXISTS idx_executions_opposite_order ON executions(opposite_order_id);
CREATE INDEX IF NOT EXISTS idx_trades_symbol ON trades(symbol);
CREATE INDEX IF NOT EXISTS idx_trades_trade_time ON trades(trade_time DESC);

-- ============================================================
-- SECTION 5: Initialize leaves_qty for existing orders
-- ============================================================
UPDATE orders SET leaves_qty = (quantity - cum_qty) WHERE leaves_qty IS NULL;

-- ============================================================
-- SECTION 6: Verify Updates
-- ============================================================
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'orders' 
ORDER BY ordinal_position;

SELECT 'Orders updated' AS status, COUNT(*) AS row_count FROM orders
UNION ALL
SELECT 'Executions', COUNT(*) FROM executions
UNION ALL
SELECT 'Trades', COUNT(*) FROM trades;
