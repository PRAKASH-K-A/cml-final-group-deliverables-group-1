import { Injectable } from '@angular/core';
import { Subject, BehaviorSubject, Observable } from 'rxjs';

/**
 * Order interface matching the Java Order POJO structure
 */
export interface Order {
  clOrdID: string;
  symbol: string;
  side: string;  // '1' for BUY, '2' for SELL
  price: number;
  quantity: number;
}

/**
 * WebSocketService - Manages real-time connection to Java Order Service
 * 
 * This service establishes a persistent WebSocket connection to the backend
 * and provides an Observable stream of incoming order data.
 */
@Injectable({
  providedIn: 'root'
})
export class WebsocketService {
  private socket: WebSocket | null = null;
  
  // A Subject is a multicast Observable (allows multiple components to listen)
  public messages: Subject<Order> = new Subject<Order>();
  
  // Connection status tracking - BehaviorSubject emits current value to new subscribers
  public connectionStatus: BehaviorSubject<boolean> = new BehaviorSubject<boolean>(false);

  constructor() {
    this.connect();
  }

  /**
   * Establish WebSocket connection to the backend
   */
  private connect(): void {
    // Prevent multiple connection attempts
    if (this.socket && this.socket.readyState === WebSocket.CONNECTING) {
      console.log('[WEBSOCKET] Connection already in progress...');
      return;
    }

    try {
      this.socket = new WebSocket('ws://localhost:8080');

      this.socket.onopen = (event) => {
        console.log('[WEBSOCKET] ✓ Connected to Order Service');
        this.connectionStatus.next(true);
      };

      this.socket.onmessage = (event) => {
        console.log('[WEBSOCKET] Raw data received:', event.data);
        try {
          const order = JSON.parse(event.data) as Order;
          console.log('[WEBSOCKET] Parsed order:', order);
          this.messages.next(order);
        } catch (error) {
          console.error('[WEBSOCKET] Failed to parse order data:', error);
        }
      };

      this.socket.onerror = (error) => {
        console.error('[WEBSOCKET] ✗ Error:', error);
        this.connectionStatus.next(false);
      };

      this.socket.onclose = (event) => {
        console.log('[WEBSOCKET] ✗ Connection closed. Code:', event.code, 'Reason:', event.reason);
        this.connectionStatus.next(false);
        
        // Only reconnect if not a normal closure (1000) or going away (1001)
        if (event.code !== 1000 && event.code !== 1001) {
          console.log('[WEBSOCKET] Attempting to reconnect in 5 seconds...');
          setTimeout(() => this.connect(), 5000);
        }
      };
    } catch (error) {
      console.error('[WEBSOCKET] Failed to establish connection:', error);
      this.connectionStatus.next(false);
    }
  }

  /**
   * Get the messages Observable
   */
  getMessages(): Observable<Order> {
    return this.messages.asObservable();
  }

  /**
   * Get the connection status Observable
   */
  getConnectionStatus(): Observable<boolean> {
    return this.connectionStatus.asObservable();
  }

  /**
   * Close the WebSocket connection
   */
  disconnect(): void {
    if (this.socket) {
      this.socket.close();
      this.socket = null;
    }
  }
}
