package com.cabin.express.config;

/**
 * Environment class for accessing configuration properties
 * throughout the application.
 */
public class Environment {
    private static final ConfigLoader configLoader = ConfigLoader.getInstance();

    private Environment() {
        // Private constructor to prevent instantiation
    }

    /**
     * Gets a string property
     * @param key Property key
     * @return Property value or null if not found
     */
    public static String getString(String key) {
        return configLoader.getString(key);
    }

    /**
     * Gets a string property with default value
     * @param key Property key
     * @param defaultValue Default value to return if property not found
     * @return Property value or defaultValue if not found
     */
    public static String getString(String key, String defaultValue) {
        return configLoader.getString(key, defaultValue);
    }

    /**
     * Gets an integer property
     * @param key Property key
     * @return Integer value or null if not found
     */
    public static Integer getInteger(String key) {
        return configLoader.getInteger(key);
    }

    /**
     * Gets an integer property with default value
     * @param key Property key
     * @param defaultValue Default value to return if property not found
     * @return Integer value or defaultValue if not found
     */
    public static Integer getInteger(String key, Integer defaultValue) {
        return configLoader.getInteger(key, defaultValue);
    }

    /**
     * Gets a boolean property
     * @param key Property key
     * @return Boolean value or null if not found
     */
    public static Boolean getBoolean(String key) {
        return configLoader.getBoolean(key);
    }

    /**
     * Gets a boolean property with default value
     * @param key Property key
     * @param defaultValue Default value to return if property not found
     * @return Boolean value or defaultValue if not found
     */
    public static Boolean getBoolean(String key, Boolean defaultValue) {
        return configLoader.getBoolean(key, defaultValue);
    }
}