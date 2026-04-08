package com.stocker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * DatabaseManager - Singleton for PostgreSQL Database Operations
 * 
 * Handles all database interactions for order persistence.
 * Uses JDBC with PostgreSQL driver.
 * 
 * IMPORTANT: In production, use connection pooling (HikariCP) and 
 * externalize credentials to environment variables.
 */
public class DatabaseManager {
    
    // PostgreSQL Connection Configuration
    private static final String URL = "jdbc:postgresql://localhost:5432/trading_system";
    private static final String USER = "postgres";
    private static final String PASSWORD = "root"; // CHANGE THIS to your PostgreSQL password!
    
    /**
     * Insert a new order into the PostgreSQL database
     * 
     * This method is called by the OrderPersister worker thread.
     * It uses PreparedStatement to prevent SQL injection.
     * 
     * @param order The Order object to persist
     */
    public static void insertOrder(Order order) {
        String sql = "INSERT INTO orders (order_id, cl_ord_id, symbol, side, price, quantity, status, timestamp) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Set parameters (matching Order POJO fields)
            pstmt.setString(1, order.getOrderId());
            pstmt.setString(2, order.getClOrdID());
            pstmt.setString(3, order.getSymbol());
            pstmt.setString(4, String.valueOf(order.getSide()));
            pstmt.setDouble(5, order.getPrice());
            pstmt.setDouble(6, order.getQuantity());
            pstmt.setString(7, "NEW"); // Initial status
            pstmt.setTimestamp(8, Timestamp.from(Instant.now()));
            
            // Execute the insert
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                System.out.println("[DATABASE] ✓ Order persisted: " + order.getClOrdID() + 
                                   " (" + order.getSymbol() + ")");
            }
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Failed to persist order: " + order.getClOrdID());
            System.err.println("[DATABASE] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Test database connectivity
     * 
     * Call this at startup to verify PostgreSQL is accessible
     * before accepting any orders.
     */
    public static boolean testConnection() {
        System.out.println("[DATABASE] Testing PostgreSQL connection...");
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD)) {
            String dbProduct = conn.getMetaData().getDatabaseProductName();
            String dbVersion = conn.getMetaData().getDatabaseProductVersion();
            
            System.out.println("[DATABASE] ✓ Connected to " + dbProduct + " " + dbVersion);
            System.out.println("[DATABASE] ✓ URL: " + URL);
            System.out.println("[DATABASE] ✓ Ready for order persistence");
            
            return true;
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Connection FAILED!");
            System.err.println("[DATABASE] ✗ Error: " + e.getMessage());
            System.err.println("[DATABASE] ✗ Make sure PostgreSQL is running and credentials are correct");
            
            return false;
        }
    }
    
    /**
     * Update order status (for future use)
     *
     * @param clOrdID Client Order ID
     * @param newStatus New status (e.g., FILLED, CANCELLED)
     */
    public static void updateOrderStatus(String clOrdID, String newStatus) {
        String sql = "UPDATE orders SET status = ? WHERE cl_ord_id = ?";
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, newStatus);
            pstmt.setString(2, clOrdID);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                System.out.println("[DATABASE] ✓ Order status updated: " + clOrdID + " -> " + newStatus);
            }
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Failed to update order: " + clOrdID);
            e.printStackTrace();
        }
    }
    
    /**
     * Load all securities from security_master into a HashMap for fast in-memory lookup.
     * Called once at startup. O(1) symbol validation thereafter.
     *
     * @return Map of symbol -> Security object
     */
    public static Map<String, Security> loadSecurityMaster() {
        Map<String, Security> securities = new HashMap<>();
        String sql = "SELECT symbol, security_type, description, underlying, lot_size FROM security_master";
        
        System.out.println("[DATABASE] Loading Security Master from PostgreSQL...");
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Security sec = new Security();
                sec.setSymbol(rs.getString("symbol"));
                sec.setSecurityType(rs.getString("security_type"));
                sec.setDescription(rs.getString("description"));
                sec.setUnderlying(rs.getString("underlying"));
                sec.setLotSize(rs.getInt("lot_size"));
                securities.put(sec.getSymbol(), sec);
            }
            
            System.out.println("[DATABASE] ✓ Security Master loaded: " + securities.size() + " securities");
            securities.forEach((k, v) -> System.out.println("  - " + k + " (" + v.getSecurityType() + ")"));
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Failed to load Security Master: " + e.getMessage());
            e.printStackTrace();
        }
        
        return securities;
    }
    
    /**
     * Load all customers from customer_master into a HashMap for fast credit limit checks.
     * Called once at startup.
     *
     * @return Map of customerCode -> Customer object
     */
    public static Map<String, Customer> loadCustomerMaster() {
        Map<String, Customer> customers = new HashMap<>();
        String sql = "SELECT customer_code, customer_name, customer_type, credit_limit FROM customer_master";
        
        System.out.println("[DATABASE] Loading Customer Master from PostgreSQL...");
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                Customer cust = new Customer();
                cust.setCustomerCode(rs.getString("customer_code"));
                cust.setCustomerName(rs.getString("customer_name"));
                cust.setCustomerType(rs.getString("customer_type"));
                cust.setCreditLimit(rs.getBigDecimal("credit_limit"));
                customers.put(cust.getCustomerCode(), cust);
            }
            
            System.out.println("[DATABASE] ✓ Customer Master loaded: " + customers.size() + " customers");
            customers.forEach((k, v) -> System.out.println("  - " + k + " (" + v.getCustomerType() + ")"));
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Failed to load Customer Master: " + e.getMessage());
            e.printStackTrace();
        }
        
        return customers;
    }
    
    /**
     * Insert an execution record when an order is matched (Lab 7).
     *
     * @param execId   Unique execution ID
     * @param orderId  Server-side order ID (FK to orders table)
     * @param symbol   Instrument symbol
     * @param side     '1' = BUY, '2' = SELL
     * @param execQty  Quantity executed
     * @param execPrice Price at which execution occurred
     */
    public static void insertExecution(String execId, String orderId, String symbol,
                                       char side, int execQty, double execPrice) {
        String sql = "INSERT INTO executions (exec_id, order_id, symbol, side, exec_qty, exec_price) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(URL, USER, PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, execId);
            pstmt.setString(2, orderId);
            pstmt.setString(3, symbol);
            pstmt.setString(4, String.valueOf(side));
            pstmt.setInt(5, execQty);
            pstmt.setDouble(6, execPrice);
            
            pstmt.executeUpdate();
            System.out.println("[DATABASE] ✓ Execution persisted: " + execId + " | " + symbol +
                               " " + execQty + " @ " + execPrice);
            
        } catch (SQLException e) {
            System.err.println("[DATABASE] ✗ Failed to persist execution: " + execId);
            e.printStackTrace();
        }
    }
}
