package com.cabin.express.zdemo.db;

import com.cabin.express.zdemo.dto.Product;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductMySQL {
    public static final ProductMySQL Instance = new ProductMySQL();

    private ProductMySQL() {
    }

    public int addProduct(Product product) {
        int rs = -1;

        try (Connection connection = HikariCPDataSource.getConnection()) {
            String sql = "INSERT INTO product(product_name, product_desc, price) VALUES(?, ?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setString(1, product.getProductName());
                preparedStatement.setString(2, product.getProductDesc());
                preparedStatement.setDouble(3, product.getPrice());
                rs = preparedStatement.executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return rs;
    }

    public List<Product> getSlice(int offset, int limit) throws SQLException {
        List<Product> products = new ArrayList<>();
        Connection connection = HikariCPDataSource.getConnection();
        System.err.println("Connection: " + connection);
        try  {
            String sql = "SELECT * FROM product ORDER BY product_id DESC LIMIT ?, ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                preparedStatement.setInt(1, offset);
                preparedStatement.setInt(2, limit);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        Product product = new Product();
                        product.setProductId(resultSet.getLong("product_id"));
                        product.setProductName(resultSet.getString("product_name"));
                        product.setProductDesc(resultSet.getString("product_desc"));
                        product.setPrice(resultSet.getDouble("price"));
                        products.add(product);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e);
        }
        return products;
    }
}
