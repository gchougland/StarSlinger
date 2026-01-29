package com.hexvane.starslinger.util;

import com.hypixel.hytale.logger.HytaleLogger;

/**
 * Utility class for conditional debug logging in Star Slinger.
 * Allows enabling/disabling verbose debug logs via gradle property without removing the code.
 * 
 * Set starslinger_debug_logging=true in gradle.properties to enable debug logs.
 * Warnings and errors are always logged regardless of this setting.
 */
public class DebugLogger {
    // This will be replaced at build time by gradle based on starslinger_debug_logging property
    private static final boolean DEBUG_ENABLED = false;
    
    /**
     * Logs an info message only if debug logging is enabled.
     * Use this for verbose debug output that you don't want in production.
     */
    public static void debugInfo(HytaleLogger logger, String message, Object... args) {
        if (DEBUG_ENABLED) {
            logger.atInfo().log(message, args);
        }
    }
    
    /**
     * Logs an info message only if debug logging is enabled.
     * Use this for verbose debug output that you don't want in production.
     */
    public static void debugInfo(HytaleLogger logger, String message) {
        if (DEBUG_ENABLED) {
            logger.atInfo().log(message);
        }
    }
    
    /**
     * Always logs warnings and errors - these are not affected by debug flag.
     * Use regular logger.atWarning() or logger.atError() for these.
     */
}
