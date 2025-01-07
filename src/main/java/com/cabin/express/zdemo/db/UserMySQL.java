package com.cabin.express.zdemo.db;

import com.cabin.express.zdemo.dto.UserInfo;

import java.util.ArrayList;
import java.util.List;
import java.sql.*;

public class UserMySQL {
    public static final UserMySQL Instance = new UserMySQL();

    public int addUser(UserInfo user) {
        Connection connection = null;
        Statement statement = null;
        try {
            connection = MySQLDataSource.Instance.getConnection();
            statement = connection.createStatement();
            String sql = String.format("INSERT INTO users (userId, username, email, phone, createdAt) VALUES ('%s', '%s', '%s', '%s', '%s')", user.getUserId(), user.getUsername(), user.getEmail(), user.getPhone(), new Timestamp(System.currentTimeMillis()));
            return statement.executeUpdate(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public List<UserInfo> getSlice(int offset, int limit) {
        Connection connection = null;
        Statement statement = null;
        ResultSet resultSet = null;
        List<UserInfo> users = new ArrayList<>();
        try {
            connection = MySQLDataSource.Instance.getConnection();
            statement = connection.createStatement();
            String sql = String.format("SELECT * FROM users ORDER BY id DESC LIMIT %d, %d", offset, limit);
            resultSet = statement.executeQuery(sql);
            while (resultSet.next()) {
                UserInfo user = new UserInfo();
                user.setUserId(resultSet.getLong("userId"));
                user.setUsername(resultSet.getString("username"));
                user.setEmail(resultSet.getString("email"));
                user.setPhone(resultSet.getString("phone"));
                user.setCreatedAt(resultSet.getTimestamp("createdAt"));
                users.add(user);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            MySQLDataSource.Instance.closeConnection(connection);
        }

        return users;
    }
}
