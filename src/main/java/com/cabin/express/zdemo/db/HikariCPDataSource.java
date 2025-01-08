package com.cabin.express.zdemo.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class HikariCPDataSource {
    private static final Logger logger = Logger.getLogger(HikariCPDataSource.class.getName());
    private static HikariDataSource dataSource;

    static {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/your_database");
        config.setUsername("your_username");
        config.setPassword("your_password");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.setMaximumPoolSize(10);

        // Set timeout for connection
        config.setConnectionTimeout(30000); // 30 seconds
        config.setValidationTimeout(5000);  // 5 seconds
        config.setIdleTimeout(600000);      // 10 minutes

        dataSource = new HikariDataSource(config);
    }

    public static Connection getConnection() throws SQLException {
        try {
            if (dataSource != null) {
                return dataSource.getConnection();
            }
            throw new SQLException("DataSource is not initialized.");
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to get connection from DataSource", e);
            throw e;
        }
    }

    public static void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}