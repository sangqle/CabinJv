package com.cabin.express.config;

import com.cabin.express.loggger.CabinLogger;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Configuration loader for the CabinJV framework.
 * Loads configurations from *.properties files in the classpath.
 * application.properties has highest priority and overrides other property files.
 */
public class ConfigLoader {
    private static final String DEFAULT_PROPERTIES = "application.properties";
    private static final String LOCAL_PROPERTIES = "local-application.properties";
private static final String PROPERTIES_SUFFIX = ".properties";

private static ConfigLoader instance;
    private final Properties configProperties = new Properties();
    private final List<String> loadedFiles = new ArrayList<>();

    private ConfigLoader() {
        loadProperties();
    }

    /**
     * Get singleton instance of ConfigLoader
     * @return ConfigLoader instance
     */
    public static synchronized ConfigLoader getInstance() {
        if (instance == null) {
            instance = new ConfigLoader();
        }
        return instance;
    }

    /**
     * Loads properties files with priority order
     */
    private void loadProperties() {
        try {
            // Load all *.properties files except application.properties and local-application.properties
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            Iterator<InputStream> resources = getPropertiesResources(classLoader);

            // Load other properties files first (lowest priority)
            while (resources.hasNext()) {
                try (InputStream is = resources.next()) {
                    Properties props = new Properties();
                    props.load(is);
                    configProperties.putAll(props);
                }
            }

            // Load local-application.properties (medium priority)
            loadSpecificPropertiesFile(LOCAL_PROPERTIES);

            // Load application.properties (highest priority)
            loadSpecificPropertiesFile(DEFAULT_PROPERTIES);

            CabinLogger.info("Loaded configuration from: " + String.join(", ", loadedFiles));
        } catch (IOException e) {
            CabinLogger.error("Failed to load configuration properties", e);
        }
    }

    private void loadSpecificPropertiesFile(String fileName) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                configProperties.putAll(props);
                loadedFiles.add(fileName);
            }
        } catch (IOException e) {
            CabinLogger.warn("Could not load " + fileName + ": " + e.getMessage());
        }
    }

    private Iterator<InputStream> getPropertiesResources(ClassLoader classLoader) throws IOException {
        List<InputStream> resources = new ArrayList<>();
        Enumeration<java.net.URL> urls = classLoader.getResources("");

        while (urls.hasMoreElements()) {
            java.net.URL url = urls.nextElement();
            if (url.getProtocol().equals("file")) {
                java.io.File file = new java.io.File(url.getPath());
                findPropertiesFiles(file, resources, classLoader);
            }
        }

        return resources.iterator();
    }

    private void findPropertiesFiles(java.io.File directory, List<InputStream> resources, ClassLoader classLoader) {
        if (directory.isDirectory()) {
            for (java.io.File file : Objects.requireNonNull(directory.listFiles())) {
                if (file.isDirectory()) {
                    findPropertiesFiles(file, resources, classLoader);
                } else if (file.getName().endsWith(PROPERTIES_SUFFIX) &&
                          !file.getName().equals(DEFAULT_PROPERTIES) &&
                          !file.getName().equals(LOCAL_PROPERTIES)) {
                    try {
                        String relativePath = getRelativePath(file);
                        InputStream is = classLoader.getResourceAsStream(relativePath);
                        if (is != null) {
                            resources.add(is);
                            loadedFiles.add(relativePath);
                        }
                    } catch (Exception e) {
                        CabinLogger.warn("Could not process: " + file.getName() + " - " + e.getMessage());
                    }
                }
            }
        }
    }

    private String getRelativePath(java.io.File file) {
        String path = file.getPath();
        int resourcesIndex = path.indexOf("resources");
        if (resourcesIndex != -1) {
            return path.substring(resourcesIndex + 10); // "resources/".length() = 10
        }
        return file.getName();
    }

    /**
     * Gets a string property
     * @param key Property key
     * @return Property value or null if not found
     */
    public String getString(String key) {
        return configProperties.getProperty(key);
    }

    /**
     * Gets a string property with default value
     * @param key Property key
     * @param defaultValue Default value to return if property not found
     * @return Property value or defaultValue if not found
     */
    public String getString(String key, String defaultValue) {
        return configProperties.getProperty(key, defaultValue);
    }

    /**
     * Gets an integer property
     * @param key Property key
     * @return Integer value or null if not found or not an integer
     */
    public Integer getInteger(String key) {
        String value = getString(key);
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            CabinLogger.warn("Failed to parse integer property: " + key + " = " + value + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets an integer property with default value
     * @param key Property key
     * @param defaultValue Default value to return if property not found
     * @return Integer value or defaultValue if not found or not an integer
     */
    public Integer getInteger(String key, Integer defaultValue) {
        Integer value = getInteger(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets a boolean property
     * @param key Property key
     * @return Boolean value or null if not found
     */
    public Boolean getBoolean(String key) {
        String value = getString(key);
        if (value == null) return null;
        return Boolean.parseBoolean(value);
    }

    /**
     * Gets a boolean property with default value
     * @param key Property key
     * @param defaultValue Default value to return if property not found
     * @return Boolean value or defaultValue if not found
     */
    public Boolean getBoolean(String key, Boolean defaultValue) {
        Boolean value = getBoolean(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Gets all property names
     * @return Set of property names
     */
    public Set<String> getPropertyNames() {
        Set<String> propertyNames = new HashSet<>();
        for (Object key : configProperties.keySet()) {
            propertyNames.add(key.toString());
        }
        return propertyNames;
    }
}