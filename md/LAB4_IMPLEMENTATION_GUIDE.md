# LAB 4: REAL-TIME FRONTEND INTEGRATION - IMPLEMENTATION GUIDE

## 🎯 IMPLEMENTATION SUMMARY

### Backend Changes (Java)

1. **Dependencies Added** ([pom.xml](c:\Users\dhruv\Coding\cmt_lab_kua\stocker\cmt\pom.xml))
   - Java-WebSocket 1.5.3
   - Gson 2.8.9

2. **OrderBroadcaster.java** - WebSocket server that:
   - Listens on port 8080
   - Maintains persistent connections with UI clients
   - Converts Order POJOs to JSON and broadcasts to all connected clients
   - Provides connection status logging

3. **AppLauncher.java** - Updated to:
   - Initialize and start WebSocket server on port 8080
   - Pass broadcaster instance to OrderApplication
   - Properly cleanup on shutdown

4. **OrderApplication.java** - Enhanced to:
   - Accept OrderBroadcaster in constructor
   - Create Order POJO from FIX messages
   - Broadcast orders to WebSocket clients immediately after parsing

### Frontend Changes (Angular)

1. **WebSocket Service** ([websocket.service.ts](c:\Users\dhruv\Coding\cmt_lab_kua\trading-ui\src\app\services\websocket.service.ts))
   - Establishes WebSocket connection to ws://localhost:8080
   - Provides Observable stream of incoming orders
   - Handles reconnection on disconnect
   - Connection status monitoring

2. **Order Grid Component** ([order-grid.component.ts](c:\Users\dhruv\Coding\cmt_lab_kua\trading-ui\src\app\components\order-grid\order-grid.component.ts))
   - Subscribes to WebSocket message stream
   - Displays orders in real-time table
   - Professional trading UI design with color-coded BUY/SELL
   - Connection status indicator
   - Clear orders functionality

3. **Routing** ([app.routes.ts](c:\Users\dhruv\Coding\cmt_lab_kua\trading-ui\src\app\app.routes.ts))
   - OrderGridComponent set as default route

4. **Styling** 
   - Professional financial trading UI design
   - Color-coded order sides (green for BUY, red for SELL)
   - Responsive table layout
   - Real-time order highlighting

## 🚀 TESTING INSTRUCTIONS

### Step 1: Start the Backend

```powershell
cd c:\Users\dhruv\Coding\cmt_lab_kua\stocker\cmt
mvn clean install
mvn exec:java
```

**Expected Console Output:**
```
[WEBSOCKET] ✓ WebSocket Server started on port 8080
[WEBSOCKET] Ready to accept UI connections on ws://localhost:8080
[ORDER SERVICE] ✓ FIX Order Service started. Listening on port 9876...
```

### Step 2: Start the Angular Frontend

Open a new terminal:

```powershell
cd c:\Users\dhruv\Coding\cmt_lab_kua\trading-ui
npm install  # if not already done
ng serve
```

Navigate to: **http://localhost:4200**

**Expected Behavior:**
- You should see the "Live Order Blotter" page
- Connection status indicator should show "Connected" (green)
- Backend console should log: `[WEBSOCKET] ✓ UI Connected: /127.0.0.1:...`

### Step 3: End-to-End Test with MiniFix

1. **Open MiniFix** (from Lab 3)
2. **Connect to Order Service** (localhost:9876)
3. **Send a Test Order:**
   - Symbol: IBM
   - Side: BUY
   - Quantity: 100
   - Price: 150.50

**Expected Result:**
- Order appears **INSTANTLY** on the web page (no page refresh needed)
- Order row shows with green BUY badge
- Backend logs show order broadcast confirmation

### Step 4: Verify Multiple Orders

Send several orders with different parameters:
- Different symbols (AAPL, MSFT, GOOGL)
- Different sides (BUY/SELL)
- Different quantities and prices

**Expected Result:**
- All orders appear in real-time
- Newest orders appear at the top
- BUY orders are green, SELL orders are red
- Notional value (Qty × Price) is calculated automatically

## 📊 ARCHITECTURE VALIDATION

✅ **FIX Protocol Layer**: MiniFix → QuickFIX/J (port 9876)
✅ **Application Layer**: OrderApplication processes FIX messages
✅ **WebSocket Layer**: OrderBroadcaster pushes JSON (port 8080)
✅ **Presentation Layer**: Angular displays real-time data

Data Flow:
```
[MiniFix] --FIX--> [OrderApplication] --POJO--> [OrderBroadcaster] --JSON--> [Angular UI]
```

## 🔍 TROUBLESHOOTING

### WebSocket Connection Failed
- **Issue**: UI shows "Disconnected"
- **Solution**: Ensure backend is running and WebSocket server started on port 8080

### Orders Not Appearing
- **Issue**: Backend receives orders but UI shows none
- **Solution**: Check browser console for WebSocket errors or JSON parsing issues

### Port Already in Use
- **Issue**: Cannot start WebSocket server
- **Solution**: Kill any process using port 8080:
  ```powershell
  netstat -ano | findstr :8080
  taskkill /PID <PID> /F
  ```

## 📝 KEY FEATURES

- ✅ Real-time order broadcasting via WebSocket
- ✅ JSON serialization with Gson
- ✅ RxJS Observable pattern in Angular
- ✅ Professional trading UI design
- ✅ Connection status monitoring
- ✅ Automatic reconnection
- ✅ Color-coded order sides
- ✅ Calculated notional values

## 🎓 LEARNING OUTCOMES

1. **WebSocket vs HTTP**: Understood full-duplex communication
2. **Observable Pattern**: Implemented RxJS streams in Angular
3. **JSON Serialization**: Used Gson for POJO to JSON conversion
4. **Real-time Systems**: Built push-based data delivery
5. **Multi-tier Architecture**: Connected Application and Presentation layers

---

**Lab 4 Complete! ✅**

You now have a fully functional real-time trading dashboard that displays orders from FIX clients instantly without polling or page refresh.
