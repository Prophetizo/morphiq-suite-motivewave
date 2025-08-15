package com.morphiqlabs.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Simplified logger configuration for MotiveWave plugins.
 * Uses SLF4J Simple for minimal footprint.
 */
public class LoggerConfig {

    private static final String PROPERTIES_FILE = "logger.properties";
    private static boolean initialized = false;

    // Private constructor to prevent instantiation
    private LoggerConfig() {
    }

    private static synchronized void initialize() {
        if (!initialized) {
            initializeLogger();
            initialized = true;
        }
    }

    private static void initializeLogger() {
        Properties properties = new Properties();
        try (InputStream input = LoggerConfig.class.getClassLoader().getResourceAsStream(PROPERTIES_FILE)) {
            if (input != null) {
                System.out.println("Loading logger properties from file: " + PROPERTIES_FILE);
                properties.load(input);
                
                // Set SLF4J Simple properties
                String logLevel = properties.getProperty("log.level", "INFO");
                System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", logLevel.toLowerCase());
                System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
                System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "HH:mm:ss.SSS");
                System.setProperty("org.slf4j.simpleLogger.showThreadName", "false");
                System.setProperty("org.slf4j.simpleLogger.showLogName", "true");
                System.setProperty("org.slf4j.simpleLogger.showShortLogName", "true");
            }
        } catch (IOException e) {
            System.err.println("Failed to load logger properties file: " + e.getMessage());
        }
    }

    /**
     * Get a logger for the specified class.
     * 
     * @param clazz The class to create a logger for
     * @return SLF4J Logger instance
     */
    public static Logger getLogger(Class<?> clazz) {
        initialize(); // Ensure logger is initialized
        return LoggerFactory.getLogger(clazz);
    }

    /**
     * Set the root log level (note: SLF4J Simple doesn't support dynamic level changes)
     * 
     * @param levelStr The log level string
     */
    public static void setRootLogLevel(String levelStr) {
        // SLF4J Simple doesn't support dynamic level changes
        System.out.println("Note: SLF4J Simple doesn't support dynamic log level changes. " +
                          "Set org.slf4j.simpleLogger.defaultLogLevel system property instead.");
    }
}