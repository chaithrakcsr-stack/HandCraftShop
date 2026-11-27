package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.service.EmailService;
import com.handcraftshop.util.ApiUtil;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/admin/*")
public class AdminServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!ApiUtil.requireRole(req, resp, "admin")) return;
        String path = req.getPathInfo();
        try (Connection c = DBConnection.getConnection()) {
            if ("/dashboard".equals(path)) {
                Map<String, Object> data = new LinkedHashMap<>();
                String[][] queries = {
                    {"customers", "SELECT COUNT(*) FROM customers"},
                    {"sellers", "SELECT COUNT(*) FROM sellers"},
                    {"pendingSellers", "SELECT COUNT(*) FROM sellers WHERE approval_status='PENDING'"},
                    {"products", "SELECT COUNT(*) FROM products WHERE active=1"},
                    {"orders", "SELECT COUNT(*) FROM orders"},
                    {"sales", "SELECT COALESCE(SUM(total_amount),0) FROM orders WHERE status<>'CANCELLED'"},
                    {"payments", "SELECT COUNT(*) FROM payments"}
                };
                for (String[] q : queries) {
                    try (PreparedStatement p = c.prepareStatement(q[1]); ResultSet r = p.executeQuery()) {
                        r.next(); data.put(q[0], r.getObject(1));
                    }
                }
                ApiUtil.ok(resp, data);
            } else if ("/sellers".equals(path)) {
                ApiUtil.ok(resp, query(c, "SELECT seller_id,shop_name,owner_name,email,phone,seller_location,approval_status,craft_speciality,created_at FROM sellers ORDER BY created_at DESC"));
            } else if ("/orders".equals(path)) {
                ApiUtil.ok(resp, query(c, "SELECT id,order_number,customer_id,total_amount,status,payment_method,payment_status,delivery_city,delivery_state,created_at FROM orders ORDER BY created_at DESC LIMIT 300"));
            } else if ("/products".equals(path)) {
                ApiUtil.ok(resp, query(c, "SELECT p.id,p.name,p.category,p.price_inr,p.stock_quantity,p.active,s.shop_name FROM products p LEFT JOIN sellers s ON s.seller_id=p.seller_id ORDER BY p.id DESC LIMIT 500"));
            } else if ("/customers".equals(path)) {
                ApiUtil.ok(resp, query(c, "SELECT customer_id,full_name,email,phone,active,created_at FROM customers ORDER BY created_at DESC LIMIT 500"));
            } else if ("/categories".equals(path)) {
                ApiUtil.ok(resp, query(c, "SELECT category_id,name,description,image_url,sort_order,active FROM categories ORDER BY sort_order,name"));
            } else if ("/payments".equals(path)) {
                ApiUtil.ok(resp, query(c, "SELECT payment_id,order_id,provider,amount,status,transaction_reference,created_at FROM payments ORDER BY created_at DESC LIMIT 300"));
            } else if ("/reports".equals(path)) {
                Map<String, Object> report = new LinkedHashMap<>();
                report.put("monthlySales", query(c, "SELECT DATE_FORMAT(created_at,'%Y-%m') AS period,COUNT(*) AS order_count,SUM(total_amount) AS revenue FROM orders WHERE status<>'CANCELLED' GROUP BY DATE_FORMAT(created_at,'%Y-%m') ORDER BY period DESC LIMIT 12"));
                report.put("categorySales", query(c, "SELECT p.category,SUM(oi.quantity) AS units_sold,SUM(oi.quantity*oi.unit_price) AS gross_sales FROM order_items oi JOIN products p ON p.id=oi.product_id JOIN orders o ON o.id=oi.order_id WHERE o.status<>'CANCELLED' GROUP BY p.category ORDER BY gross_sales DESC LIMIT 20"));
                report.put("paymentSummary", query(c, "SELECT provider,status,COUNT(*) AS payment_count,SUM(amount) AS amount_total FROM payments GROUP BY provider,status ORDER BY provider,status"));
                ApiUtil.ok(resp, report);
            } else {
                ApiUtil.error(resp, 404, "Unknown admin endpoint.");
            }
        } catch (SQLException e) {
            ApiUtil.error(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!ApiUtil.requireRole(req, resp, "admin")) return;
        String path = req.getPathInfo();
        JsonObject b = ApiUtil.readJson(req);
        if (!"/categories".equals(path)) {
            ApiUtil.error(resp, 404, "Unknown admin endpoint.");
            return;
        }
        String name = ApiUtil.string(b, "name", "");
        if (name.isBlank()) {
            ApiUtil.error(resp, 400, "Category name is required.");
            return;
        }
        try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(
                "INSERT INTO categories(name,description,image_url,sort_order,active) VALUES(?,?,?,?,1)")) {
            p.setString(1, name);
            p.setString(2, ApiUtil.string(b, "description", ""));
            p.setString(3, ApiUtil.string(b, "imageUrl", ""));
            p.setInt(4, ApiUtil.integer(b, "sortOrder", 100));
            p.executeUpdate();
            ApiUtil.send(resp, 201, Map.of("success", true, "message", "Category created."));
        } catch (SQLException e) {
            ApiUtil.error(resp, e.getErrorCode() == 1062 ? 409 : 500,
                    e.getErrorCode() == 1062 ? "That category already exists." : e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!ApiUtil.requireRole(req, resp, "admin")) return;
        String path = req.getPathInfo();
        JsonObject b = ApiUtil.readJson(req);
        try (Connection c = DBConnection.getConnection()) {
            if ("/sellers".equals(path)) {
                int sellerId = ApiUtil.integer(b, "sellerId", 0);
                String status = ApiUtil.string(b, "status", "").toUpperCase(Locale.ROOT);
                if (!Set.of("APPROVED", "REJECTED", "PENDING").contains(status)) {
                    ApiUtil.error(resp, 400, "Invalid seller approval status."); return;
                }
                String email = "";
                try (PreparedStatement p = c.prepareStatement("UPDATE sellers SET approval_status=?,verification_note=? WHERE seller_id=?")) {
                    p.setString(1, status); p.setString(2, ApiUtil.string(b, "note", "")); p.setInt(3, sellerId);
                    if (p.executeUpdate() == 0) { ApiUtil.error(resp, 404, "Seller not found."); return; }
                }
                try (PreparedStatement p = c.prepareStatement("SELECT email FROM sellers WHERE seller_id=?")) {
                    p.setInt(1, sellerId);
                    try (ResultSet r = p.executeQuery()) { if (r.next()) email = r.getString(1); }
                }
                EmailService.send(email, "Handcraft Shop seller application", "Your seller application status is: " + status + ".");
                ApiUtil.ok(resp, Map.of("success", true, "message", "Seller status changed to " + status + "."));
            } else if ("/orders".equals(path)) {
                long id = b.has("orderId") ? b.get("orderId").getAsLong() : 0;
                String status = ApiUtil.string(b, "status", "").toUpperCase(Locale.ROOT);
                if (!Set.of("PLACED", "CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED").contains(status)) {
                    ApiUtil.error(resp, 400, "Invalid order status."); return;
                }
                String email = "", number = "";
                c.setAutoCommit(false);
                try (PreparedStatement p = c.prepareStatement("SELECT guest_email,order_number FROM orders WHERE id=? FOR UPDATE")) {
                    p.setLong(1, id);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) { c.rollback(); ApiUtil.error(resp, 404, "Order not found."); return; }
                        email = r.getString(1); number = r.getString(2);
                    }
                }
                if ("CANCELLED".equals(status)) {
                    try (PreparedStatement p = c.prepareStatement("UPDATE products p JOIN order_items oi ON oi.product_id=p.id SET p.stock_quantity=p.stock_quantity+oi.quantity WHERE oi.order_id=? AND oi.item_status<>'CANCELLED'")) { p.setLong(1, id); p.executeUpdate(); }
                    try (PreparedStatement p = c.prepareStatement("UPDATE payments SET status='CANCELLED' WHERE order_id=?")) { p.setLong(1, id); p.executeUpdate(); }
                }
                try (PreparedStatement p = c.prepareStatement("UPDATE orders SET status=? WHERE id=?")) { p.setString(1, status); p.setLong(2, id); p.executeUpdate(); }
                try (PreparedStatement p = c.prepareStatement("UPDATE seller_orders SET status=?,updated_at=NOW() WHERE order_id=?")) { p.setString(1, status); p.setLong(2, id); p.executeUpdate(); }
                try (PreparedStatement p = c.prepareStatement("UPDATE order_items SET item_status=? WHERE order_id=?")) { p.setString(1, status); p.setLong(2, id); p.executeUpdate(); }
                c.commit();
                EmailService.send(email, "Handcraft Shop order update - " + number, "Your marketplace order status is now " + status + ".");
                ApiUtil.ok(resp, Map.of("success", true, "message", "Order updated."));
            } else if ("/products".equals(path)) {
                int id = ApiUtil.integer(b, "productId", 0);
                boolean active = b.has("active") && b.get("active").getAsBoolean();
                try (PreparedStatement p = c.prepareStatement("UPDATE products SET active=? WHERE id=?")) {
                    p.setBoolean(1, active); p.setInt(2, id); p.executeUpdate();
                }
                ApiUtil.ok(resp, Map.of("success", true, "message", "Product moderation updated."));
            } else if ("/customers".equals(path)) {
                int id = ApiUtil.integer(b, "customerId", 0);
                boolean active = b.has("active") && b.get("active").getAsBoolean();
                try (PreparedStatement p = c.prepareStatement("UPDATE customers SET active=? WHERE customer_id=?")) {
                    p.setBoolean(1, active); p.setInt(2, id);
                    if (p.executeUpdate() == 0) { ApiUtil.error(resp, 404, "Customer not found."); return; }
                }
                ApiUtil.ok(resp, Map.of("success", true, "message", active ? "Customer account activated." : "Customer account disabled."));
            } else if ("/categories".equals(path)) {
                int id = ApiUtil.integer(b, "categoryId", 0);
                if (id < 1) { ApiUtil.error(resp, 400, "Category id is required."); return; }
                try (PreparedStatement p = c.prepareStatement("UPDATE categories SET name=?,description=?,image_url=?,sort_order=?,active=? WHERE category_id=?")) {
                    p.setString(1, ApiUtil.string(b, "name", ""));
                    p.setString(2, ApiUtil.string(b, "description", ""));
                    p.setString(3, ApiUtil.string(b, "imageUrl", ""));
                    p.setInt(4, ApiUtil.integer(b, "sortOrder", 100));
                    p.setBoolean(5, !b.has("active") || b.get("active").getAsBoolean());
                    p.setInt(6, id);
                    if (p.executeUpdate() == 0) { ApiUtil.error(resp, 404, "Category not found."); return; }
                }
                ApiUtil.ok(resp, Map.of("success", true, "message", "Category updated."));
            } else {
                ApiUtil.error(resp, 404, "Unknown admin endpoint.");
            }
        } catch (SQLException e) {
            ApiUtil.error(resp, e.getErrorCode() == 1062 ? 409 : 500,
                    e.getErrorCode() == 1062 ? "That category name is already in use." : e.getMessage());
        }
    }

    private List<Map<String, Object>> query(Connection c, String sql) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement(sql); ResultSet r = p.executeQuery()) {
            ResultSetMetaData md = r.getMetaData();
            while (r.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = 1; i <= md.getColumnCount(); i++) m.put(md.getColumnLabel(i), r.getObject(i));
                rows.add(m);
            }
        }
        return rows;
    }
}
