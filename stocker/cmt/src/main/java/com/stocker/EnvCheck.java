package com.stocker;

import quickfix.Message;
import quickfix.SessionID;

public class EnvCheck {
    public static void main(String[] args) {
        System.out.println("--- ENVIRONMENT DIAGNOSTIC ---");
        
        // Check Java Version
        System.out.println("Java Version: " + System.getProperty("java.version"));
        
        // Check QuickFIX Library Accessibility
        try {
            // Attempt to instantiate a core QuickFIX object
            quickfix.Message msg = new quickfix.Message();
            
            // Attempt to use a basic FIX type
            SessionID session = new SessionID("FIX.4.4", "SENDER", "TARGET");
            
            System.out.println("QuickFIX/J Library: DETECTED & FUNCTIONAL");
            System.out.println("Test Message Constructed: " + msg.toString());
            
        } catch (NoClassDefFoundError | Exception e) {
            System.err.println("CRITICAL ERROR: QuickFIX/J libraries not found in Classpath.");
            e.printStackTrace();
        }
    }
}
