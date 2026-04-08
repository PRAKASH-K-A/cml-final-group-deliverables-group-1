-- ============================================================
-- Setup Script for Trading System Database
-- Run this to set up everything at once
-- ============================================================

-- Create orders table first (required for foreign key in executions)
CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(50) PRIMARY KEY,
    cl_ord_id VARCHAR(50) NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side CHAR(1) NOT NULL,
    price DECIMAL(15, 2) NOT NULL,
    quantity DECIMAL(15, 0) NOT NULL,
    status VARCHAR(20) DEFAULT 'NEW',
    timestamp TIMESTAMPTZ DEFAULT NOW()
);

-- Create indexes for fast queries
CREATE INDEX IF NOT EXISTS idx_orders_symbol ON orders(symbol);
CREATE INDEX IF NOT EXISTS idx_orders_timestamp ON orders(timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);

-- ============================================================
-- SECTION 1: Security Master Table
-- ============================================================
CREATE TABLE IF NOT EXISTS security_master (
    symbol          VARCHAR(20)  PRIMARY KEY,
    security_type   VARCHAR(20)  NOT NULL,
    description     VARCHAR(100) NOT NULL,
    underlying      VARCHAR(20)  DEFAULT NULL,
    lot_size        INT          NOT NULL DEFAULT 1
);

-- ============================================================
-- SECTION 2: Customer Master Table
-- ============================================================
CREATE TABLE IF NOT EXISTS customer_master (
    customer_code   VARCHAR(20)     PRIMARY KEY,
    customer_name   VARCHAR(100)    NOT NULL,
    customer_type   VARCHAR(20)     NOT NULL,
    credit_limit    DECIMAL(20, 2)  NOT NULL DEFAULT 0.00
);

-- ============================================================
-- SECTION 3: Executions Table
-- ============================================================
CREATE TABLE IF NOT EXISTS executions (
    exec_id     VARCHAR(50)     PRIMARY KEY,
    order_id    VARCHAR(50)     NOT NULL,
    symbol      VARCHAR(20)     NOT NULL,
    side        CHAR(1)         NOT NULL,
    exec_qty    INT             NOT NULL,
    exec_price  DECIMAL(15, 2)  NOT NULL,
    match_time  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
);

-- ============================================================
-- SECTION 4: Reference Data - Securities
-- ============================================================
INSERT INTO security_master (symbol, security_type, description, lot_size) VALUES
    ('GOOG',  'CS',  'Alphabet Inc. Class A',       1),
    ('MSFT',  'CS',  'Microsoft Corporation',        1),
    ('IBM',   'CS',  'IBM Corporation',              1),
    ('AAPL',  'CS',  'Apple Inc.',                   1),
    ('AMZN',  'CS',  'Amazon.com Inc.',              1),
    ('TSLA',  'CS',  'Tesla Inc.',                   1),
    ('SPY',   'ETF', 'SPDR S&P 500 ETF Trust',      10),
    ('QQQ',   'ETF', 'Invesco QQQ Trust',            10)
ON CONFLICT (symbol) DO UPDATE
    SET description   = EXCLUDED.description,
        security_type = EXCLUDED.security_type,
        lot_size      = EXCLUDED.lot_size;

-- ============================================================
-- SECTION 5: Reference Data - Customers
-- ============================================================
INSERT INTO customer_master (customer_code, customer_name, customer_type, credit_limit) VALUES
    ('CLIENT_A', 'Retail Client Alpha',           'RETAIL',         500000.00),
    ('CLIENT_B', 'Institutional Client Bravo',    'INSTITUTIONAL',  50000000.00),
    ('CLIENT_C', 'Institutional Client Charlie',  'INSTITUTIONAL',  100000000.00),
    ('CLIENT_D', 'Retail Client Delta',           'RETAIL',         250000.00)
ON CONFLICT (customer_code) DO UPDATE
    SET customer_name = EXCLUDED.customer_name,
        credit_limit  = EXCLUDED.credit_limit;

-- ============================================================
-- SECTION 6: Indexes for performance
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_executions_order_id  ON executions (order_id);
CREATE INDEX IF NOT EXISTS idx_executions_symbol     ON executions (symbol);
CREATE INDEX IF NOT EXISTS idx_executions_match_time ON executions (match_time DESC);

-- ============================================================
-- VERIFY
-- ============================================================
SELECT 'security_master' AS table_name, COUNT(*) AS row_count FROM security_master
UNION ALL
SELECT 'customer_master', COUNT(*) FROM customer_master
UNION ALL
SELECT 'orders',          COUNT(*) FROM orders
UNION ALL
SELECT 'executions',      COUNT(*) FROM executions;
