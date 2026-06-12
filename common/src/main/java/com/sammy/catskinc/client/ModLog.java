package com.sammy.catskinc.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("catskinc");

    private static volatile boolean debugEnabled;
    private static volatile boolean traceEnabled;

    private ModLog() {
    }

    public static void configure(boolean debug, boolean trace) {
        debugEnabled = debug || trace;
        traceEnabled = trace;
        info("Logging configured: debug={}, trace={}", debugEnabled, traceEnabled);
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static boolean isTraceEnabled() {
        return traceEnabled;
    }

    public static void info(String message, Object... args) {
        LOGGER.info(message, args);
    }

    public static void warn(String message, Object... args) {
        LOGGER.warn(message, args);
    }

    public static void warn(String message, Throwable throwable) {
        LOGGER.warn(message, throwable);
    }

    public static void error(String message, Object... args) {
        LOGGER.error(message, args);
    }

    public static void error(String message, Throwable throwable) {
        LOGGER.error(message, throwable);
    }

    public static void debug(String message, Object... args) {
        if (debugEnabled) {
            LOGGER.info("[DEBUG] " + message, args);
        }
    }

    public static void debug(String message, Throwable throwable) {
        if (debugEnabled) {
            LOGGER.info("[DEBUG] " + message, throwable);
        }
    }

    public static void trace(String message, Object... args) {
        if (traceEnabled) {
            LOGGER.info("[TRACE] " + message, args);
        }
    }

    public static void trace(String message, Throwable throwable) {
        if (traceEnabled) {
            LOGGER.info("[TRACE] " + message, throwable);
        }
    }
}

