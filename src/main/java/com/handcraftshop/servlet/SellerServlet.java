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

@WebServlet("/api/seller/*")
public class SellerServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!ApiUtil.requireRole(req, resp, "seller")) return;
        String path = req.getPathInfo();
        int sellerId = ApiUtil.sessionId(req);
        try (Connection c = DBConnection.getConnection()) {
            if ("/profile".equals(path)) {
                try (PreparedStatement p = c.prepareStatement("SELECT seller_id,shop_name,owner_name,email,phone,seller_location,shop_description,craft_speciality,approval_status FROM sellers WHERE seller_id=?")) {
                    p.setInt(1, sellerId);
                    try (ResultSet r = p.executeQuery()) {
                        if (!r.next()) { ApiUtil.error(resp, 404, "Seller profile not found."); return; }
                        Map<String, Object> m = new LinkedHashMap<>();
                        for (String k : List.of("seller_id", "shop_name", "owner_name", "email", "phone", "seller_location", "shop_description", "craft_speciality", "approval_status")) m.put(k, r.getObject(k));
                        ApiUtil.ok(resp, m);
                    }
                }
            } else if ("/dashboard".equals(path)) {
                Map<String, Object> out = new LinkedHashMap<>();
                try (PreparedStatement p = c.prepareStatement("SELECT approval_status,shop_name,seller_location FROM sellers WHERE seller_id=?")) {
                    p.setInt(1, sellerId);
                    try (ResultSet r = p.executeQuery()) {
                        if (r.next()) { out.put("approvalStatus", r.getString(1)); out.put("shopName", r.getString(2)); out.put("location", r.getString(3)); }
                    }
                }
                String[][] metrics = {
                    {"productCount", "SELECT COUNT(*) FROM products WHERE seller_id=? AND active=1"},
                    {"pendingOrders", "SELECT COUNT(*) FROM seller_orders WHERE seller_id=? AND status IN ('PLACED','CONFIRMED','PROCESSING')"},
                    {"totalOrders", "SELECT COUNT(*) FROM seller_orders WHERE seller_id=?"},
                    {"sales", "SELECT COALESCE(SUM(total_amount),0) FROM seller_orders WHERE seller_id=? AND status<>'CANCELLED'"}
                };
                for (String[] metric : metrics) {
                    try (PreparedStatement p = c.prepareStatement(metric[1])) {
                        p.setInt(1, sellerId);
                        try (ResultSet r = p.executeQuery()) { r.next(); out.put(metric[0], r.getObject(1)); }
                    }
                }
                ApiUtil.ok(resp, out);
            } else if ("/orders".equals(path)) {
                List<Map<String, Object>> rows = new ArrayList<>();
                String sql = "SELECT so.seller_order_id,so.status,so.total_amount,so.created_at,o.order_number,o.delivery_city,o.delivery_state,oi.product_id,p.name,oi.quantity,oi.unit_price FROM seller_orders so JOIN orders o ON o.id=so.order_id JOIN order_items oi ON oi.seller_order_id=so.seller_order_id JOIN products p ON p.id=oi.product_id WHERE so.seller_id=? ORDER BY so.created_at DESC";
                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setInt(1, sellerId);
                    try (ResultSet r = p.executeQuery()) {
                        while (r.next()) {
                            Map<String, Object> m = new LinkedHashMap<>();
                            for (String k : List.of("seller_order_id", "status", "total_amount", "created_at", "order_number", "delivery_city", "delivery_state", "product_id", "name", "quantity", "unit_price")) m.put(k, r.getObject(k));
                            rows.add(m);
                        }
                    }
                }
                ApiUtil.ok(resp, rows);
            } else {
                ApiUtil.error(resp, 404, "Unknown seller endpoint.");
            }
        } catch (SQLException e) {
            ApiUtil.error(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!ApiUtil.requireRole(req, resp, "seller")) return;
        String path = req.getPathInfo();
        JsonObject body = ApiUtil.readJson(req);
        int sellerId = ApiUtil.sessionId(req);

        if ("/profile".equals(path)) {
            String shop = ApiUtil.string(body, "shopName", "");
            String owner = ApiUtil.string(body, "ownerName", "");
            String location = ApiUtil.string(body, "sellerLocation", "");
            if (shop.isBlank() || owner.isBlank() || location.isBlank()) {
                ApiUtil.error(resp, 400, "Shop name, owner name and location are required."); return;
            }
            try (Connection c = DBConnection.getConnection(); PreparedStatement p = c.prepareStatement(
                    "UPDATE sellers SET shop_name=?,owner_name=?,phone=?,seller_location=?,shop_description=?,craft_speciality=?,updated_at=NOW() WHERE seller_id=?")) {
                p.setString(1, shop); p.setString(2, owner); p.setString(3, ApiUtil.string(body, "phone", ""));
                p.setString(4, location); p.setString(5, ApiUtil.string(body, "shopDescription", ""));
                p.setString(6, ApiUtil.string(body, "craftSpeciality", "")); p.setInt(7, sellerId);
                p.executeUpdate(); ApiUtil.ok(resp, Map.of("success", true, "message", "Shop profile updated."));
            } catch (SQLException e) { ApiUtil.error(resp, 500, e.getMessage()); }
            return;
        }

        if (!"/orders".equals(path)) { ApiUtil.error(resp, 404, "Unknown seller endpoint."); return; }
        long sellerOrderId = body.has("sellerOrderId") ? body.get("sellerOrderId").getAsLong() : 0;
        String status = ApiUtil.string(body, "status", "").toUpperCase(Locale.ROOT);
        if (!Set.of("CONFIRMED", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED").contains(status)) {
            ApiUtil.error(resp, 400, "Invalid order status."); return;
        }

        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            long orderId;
            String customerEmail, orderNumber, currentStatus;
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT so.order_id,so.status,o.guest_email,o.order_number FROM seller_orders so JOIN orders o ON o.id=so.order_id WHERE so.seller_order_id=? AND so.seller_id=? FOR UPDATE")) {
                p.setLong(1, sellerOrderId); p.setInt(2, sellerId);
                try (ResultSet r = p.executeQuery()) {
                    if (!r.next()) { c.rollback(); ApiUtil.error(resp, 404, "Seller order not found."); return; }
                    orderId = r.getLong(1); currentStatus = r.getString(2); customerEmail = r.getString(3); orderNumber = r.getString(4);
                }
            }
            Map<String,Integer> stage = Map.of("PLACED",0,"CONFIRMED",1,"PROCESSING",2,"SHIPPED",3,"DELIVERED",4);
            if ("CANCELLED".equals(currentStatus) && !"CANCELLED".equals(status)) {
                c.rollback(); ApiUtil.error(resp,409,"A cancelled seller order cannot be reopened."); return;
            }
            if ("CANCELLED".equals(status)) {
                if (!Set.of("PLACED","CONFIRMED","PROCESSING").contains(currentStatus)) { c.rollback(); ApiUtil.error(resp,409,"A seller order cannot be cancelled after it has shipped or been delivered."); return; }
            } else if (stage.getOrDefault(status,-1) < stage.getOrDefault(currentStatus,0)) {
                c.rollback(); ApiUtil.error(resp,409,"Seller order statuses cannot be moved backwards."); return;
            }

            if ("CANCELLED".equals(status)) {
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE products p JOIN order_items oi ON oi.product_id=p.id SET p.stock_quantity=p.stock_quantity+oi.quantity WHERE oi.seller_order_id=? AND oi.item_status<>'CANCELLED'")) {
                    p.setLong(1, sellerOrderId); p.executeUpdate();
                }
            }
            try (PreparedStatement p = c.prepareStatement("UPDATE seller_orders SET status=?,updated_at=NOW() WHERE seller_order_id=? AND seller_id=?")) {
                p.setString(1, status); p.setLong(2, sellerOrderId); p.setInt(3, sellerId); p.executeUpdate();
            }
            try (PreparedStatement p = c.prepareStatement("UPDATE order_items SET item_status=? WHERE seller_order_id=?")) {
                p.setString(1, status); p.setLong(2, sellerOrderId); p.executeUpdate();
            }

            int totalSubOrders = 0, delivered = 0, shipped = 0, processing = 0, confirmed = 0, cancelled = 0;
            try (PreparedStatement p = c.prepareStatement(
                    "SELECT COUNT(*) AS total_count,SUM(status='DELIVERED') AS delivered_count,SUM(status='SHIPPED') AS shipped_count,SUM(status='PROCESSING') AS processing_count,SUM(status='CONFIRMED') AS confirmed_count,SUM(status='CANCELLED') AS cancelled_count FROM seller_orders WHERE order_id=?")) {
                p.setLong(1, orderId);
                try (ResultSet r = p.executeQuery()) {
                    if (r.next()) {
                        totalSubOrders = r.getInt("total_count"); delivered = r.getInt("delivered_count"); shipped = r.getInt("shipped_count");
                        processing = r.getInt("processing_count"); confirmed = r.getInt("confirmed_count"); cancelled = r.getInt("cancelled_count");
                    }
                }
            }
            int nonSellerItems = 0;
            try (PreparedStatement p = c.prepareStatement("SELECT COUNT(*) FROM order_items WHERE order_id=? AND seller_order_id IS NULL")) {
                p.setLong(1, orderId); try (ResultSet r = p.executeQuery()) { r.next(); nonSellerItems = r.getInt(1); }
            }
            String parentStatus = null;
            if (totalSubOrders > 0 && cancelled == totalSubOrders && nonSellerItems == 0) parentStatus = "CANCELLED";
            else if (totalSubOrders > 0 && delivered + cancelled == totalSubOrders && nonSellerItems == 0) parentStatus = "DELIVERED";
            else if (shipped > 0 || delivered > 0) parentStatus = "SHIPPED";
            else if (processing > 0) parentStatus = "PROCESSING";
            else if (confirmed > 0) parentStatus = "CONFIRMED";
            if (parentStatus != null) {
                try (PreparedStatement p = c.prepareStatement("UPDATE orders SET status=? WHERE id=?")) {
                    p.setString(1, parentStatus); p.setLong(2, orderId); p.executeUpdate();
                }
            }
            c.commit();
            EmailService.send(customerEmail, "Handcraft Shop order update - " + orderNumber,
                    "The artisan seller updated the fulfilment status of your marketplace order to " + status + ".");
            ApiUtil.ok(resp, Map.of("success", true, "message", "Order status updated."));
        } catch (SQLException e) {
            ApiUtil.error(resp, 500, e.getMessage());
        }
    }
}
