package com.cabin.express.zdemo.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class MySQLDataSource {
    private String url = "jdbc:mysql://localhost:3306/cabin-db";
    private String username = "root";
    private String password = "123456789";

    public static final MySQLDataSource Instance = new MySQLDataSource();

    private MySQLDataSource() {
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    public void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}