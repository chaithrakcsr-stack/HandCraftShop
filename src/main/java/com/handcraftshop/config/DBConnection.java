package com.handcraftshop.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String URL = System.getenv().getOrDefault(
        "HANDCRAFT_DB_URL", "jdbc:mysql://localhost:3306/handcraft_shop?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC");
    private static final String USER = System.getenv().getOrDefault("HANDCRAFT_DB_USER", "root");
    private static final String PASSWORD = System.getenv().getOrDefault("HANDCRAFT_DB_PASSWORD", "chaithra@123");

    static {
        try { Class.forName("com.mysql.cj.jdbc.Driver"); }
        catch (ClassNotFoundException e) { throw new ExceptionInInitializerError(e); }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
