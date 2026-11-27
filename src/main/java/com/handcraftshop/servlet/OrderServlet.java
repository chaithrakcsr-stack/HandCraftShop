
package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.service.EmailService;
import com.handcraftshop.util.ApiUtil;
import com.google.gson.*;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

@WebServlet("/api/orders/*")
public class OrderServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        if (!ApiUtil.requireRole(req, resp, "customer")) {
            return;
        }

        int customerId = ApiUtil.sessionId(req);

        List<Map<String, Object>> orders = new ArrayList<>();

        String sql = "SELECT o.id,o.order_number,o.total_amount,o.status,"
                + "o.payment_method,o.payment_status,o.delivery_address,"
                + "o.delivery_city,o.delivery_state,o.delivery_pincode,"
                + "o.created_at,oi.product_id,p.name,oi.quantity,"
                + "oi.unit_price,oi.item_status "
                + "FROM orders o "
                + "LEFT JOIN order_items oi ON oi.order_id=o.id "
                + "LEFT JOIN products p ON p.id=oi.product_id "
                + "WHERE o.customer_id=? "
                + "ORDER BY o.created_at DESC,o.id DESC";

        try (Connection c = DBConnection.getConnection();
             PreparedStatement p = c.prepareStatement(sql)) {

            p.setInt(1, customerId);

            try (ResultSet r = p.executeQuery()) {

                while (r.next()) {

                    Map<String, Object> m = new LinkedHashMap<>();

                    for (String k : List.of(
                            "id",
                            "order_number",
                            "total_amount",
                            "status",
                            "payment_method",
                            "payment_status",
                            "delivery_address",
                            "delivery_city",
                            "delivery_state",
                            "delivery_pincode",
                            "created_at",
                            "product_id",
                            "name",
                            "quantity",
                            "unit_price",
                            "item_status"
                    )) {
                        m.put(k, r.getObject(k));
                    }

                    orders.add(m);
                }
            }

            ApiUtil.ok(resp, orders);

        } catch (SQLException e) {
            ApiUtil.error(resp, 500, e.getMessage());
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String path = req.getPathInfo();

        JsonObject body = ApiUtil.readJson(req);

        if ("/cancel".equals(path)) {
            cancel(body, req, resp);
            return;
        }

        placeOrder(body, req, resp);
    }

    private void placeOrder(JsonObject body,
                            HttpServletRequest req,
                            HttpServletResponse resp)
            throws IOException {

        // Only logged-in customers can place orders.
        if (!ApiUtil.requireRole(req, resp, "customer")) {
            return;
        }

        int customerId = ApiUtil.sessionId(req);

        if (body == null) {
            ApiUtil.error(resp, 400, "Invalid order request.");
            return;
        }

        JsonArray items = body.has("items")
                && body.get("items").isJsonArray()
                ? body.getAsJsonArray("items")
                : null;

        if (items == null || items.isEmpty()) {
            ApiUtil.error(resp, 400, "Your bag is empty.");
            return;
        }

        JsonObject address =
                body.has("address") && body.get("address").isJsonObject()
                ? body.getAsJsonObject("address")
                : new JsonObject();

        String recipient = ApiUtil.string(
                address,
                "recipientName",
                ApiUtil.string(body, "customerName", "")
        );

        String phone = ApiUtil.string(
                address,
                "phone",
                ApiUtil.string(body, "phone", "")
        );

        HttpSession session = req.getSession(false);

        String sessionEmail = session == null
                ? null
                : (String) session.getAttribute("email");

        String email = ApiUtil.string(body, "email", sessionEmail);

        String addressLine =
                ApiUtil.string(address, "addressLine", "");

        String city =
                ApiUtil.string(address, "city", "");

        String state =
                ApiUtil.string(address, "state", "");

        String pincode =
                ApiUtil.string(address, "pincode", "");

        if (recipient.isBlank()
                || phone.isBlank()
                || addressLine.isBlank()
                || city.isBlank()
                || state.isBlank()
                || pincode.isBlank()) {

            ApiUtil.error(
                    resp,
                    400,
                    "Enter recipient name, phone number, address, city, state and PIN code."
            );

            return;
        }

        String payment = ApiUtil.string(
                body,
                "paymentMethod",
                "TEST_PAYMENT"
        ).toUpperCase();

        if (!Set.of(
                "TEST_PAYMENT",
                "TEST_CARD",
                "TEST_UPI",
                "COD"
        ).contains(payment)) {

            ApiUtil.error(
                    resp,
                    400,
                    "Choose test payment or cash on delivery."
            );

            return;
        }

        String paymentStatus = "COD".equals(payment)
                ? "PENDING"
                : "TEST_SUCCESS";

        String orderNumber = "HS-"
                + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                .toUpperCase();

        try (Connection con = DBConnection.getConnection()) {

            con.setAutoCommit(false);

            try {

                BigDecimal total = BigDecimal.ZERO;

                List<Line> lines = new ArrayList<>();

                Map<Integer, BigDecimal> sellerTotals =
                        new LinkedHashMap<>();

                // Read product prices and lock product rows.
                String priceSql =
                        "SELECT id,name,price_inr,seller_id,stock_quantity "
                        + "FROM products "
                        + "WHERE id=? AND active=1 FOR UPDATE";

                try (PreparedStatement ps =
                             con.prepareStatement(priceSql)) {

                    for (JsonElement element : items) {

                        if (!element.isJsonObject()) {
                            throw new SQLException("Invalid cart item.");
                        }

                        JsonObject item = element.getAsJsonObject();

                        if (!item.has("productId")
                                || !item.has("quantity")) {
                            throw new SQLException("Invalid cart item details.");
                        }

                        int productId =
                                item.get("productId").getAsInt();

                        int qty =
                                item.get("quantity").getAsInt();

                        if (qty < 1 || qty > 99) {
                            throw new SQLException(
                                    "Quantity must be between 1 and 99."
                            );
                        }

                        ps.setInt(1, productId);

                        try (ResultSet rs = ps.executeQuery()) {

                            if (!rs.next()) {
                                throw new SQLException(
                                        "Product not found: " + productId
                                );
                            }

                            int stock =
                                    rs.getInt("stock_quantity");

                            String productName =
                                    rs.getString("name");

                            if (stock < qty) {
                                throw new SQLException(
                                        "Only " + stock
                                        + " item(s) are available for "
                                        + productName + "."
                                );
                            }

                            BigDecimal price =
                                    rs.getBigDecimal("price_inr");

                            Integer seller =
                                    (Integer) rs.getObject("seller_id");

                            lines.add(
                                    new Line(
                                            productId,
                                            qty,
                                            price,
                                            seller
                                    )
                            );

                            BigDecimal lineTotal =
                                    price.multiply(
                                            BigDecimal.valueOf(qty)
                                    );

                            total = total.add(lineTotal);

                            if (seller != null) {
                                sellerTotals.merge(
                                        seller,
                                        lineTotal,
                                        BigDecimal::add
                                );
                            }
                        }
                    }
                }

                long orderId;

                // Insert order for the logged-in customer.
                String insertOrder =
                        "INSERT INTO orders("
                        + "order_number,customer_id,total_amount,status,"
                        + "delivery_address,delivery_city,delivery_state,"
                        + "delivery_pincode,delivery_phone,payment_method,"
                        + "payment_status,guest_name,guest_email"
                        + ") VALUES(?,?,?,'PLACED',?,?,?,?,?,?,?, ?,?)";

                try (PreparedStatement ps = con.prepareStatement(
                        insertOrder,
                        Statement.RETURN_GENERATED_KEYS
                )) {

                    ps.setString(1, orderNumber);
                    ps.setInt(2, customerId);
                    ps.setBigDecimal(3, total);

                    ps.setString(4, addressLine);
                    ps.setString(5, city);
                    ps.setString(6, state);
                    ps.setString(7, pincode);
                    ps.setString(8, phone);

                    ps.setString(9, payment);
                    ps.setString(10, paymentStatus);

                    ps.setString(11, recipient);
                    ps.setString(12, email);

                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {

                        if (!keys.next()) {
                            throw new SQLException(
                                    "Could not generate order ID."
                            );
                        }

                        orderId = keys.getLong(1);
                    }
                }

                // Create seller orders.
                Map<Integer, Long> subOrderIds = new HashMap<>();

                for (Map.Entry<Integer, BigDecimal> entry
                        : sellerTotals.entrySet()) {

                    String sellerSql =
                            "INSERT INTO seller_orders("
                            + "order_id,seller_id,total_amount,status"
                            + ") VALUES(?,?,?,'PLACED')";

                    try (PreparedStatement ps = con.prepareStatement(
                            sellerSql,
                            Statement.RETURN_GENERATED_KEYS
                    )) {

                        ps.setLong(1, orderId);
                        ps.setInt(2, entry.getKey());
                        ps.setBigDecimal(3, entry.getValue());

                        ps.executeUpdate();

                        try (ResultSet keys = ps.getGeneratedKeys()) {

                            if (!keys.next()) {
                                throw new SQLException(
                                        "Could not create seller order."
                                );
                            }

                            subOrderIds.put(
                                    entry.getKey(),
                                    keys.getLong(1)
                            );
                        }
                    }
                }

                // Insert order items and update product stock.
                String itemSql =
                        "INSERT INTO order_items("
                        + "order_id,product_id,quantity,unit_price,"
                        + "seller_id,seller_order_id,item_status"
                        + ") VALUES(?,?,?,?,?,?, 'PLACED')";

                String stockSql =
                        "UPDATE products "
                        + "SET stock_quantity=stock_quantity-? "
                        + "WHERE id=? AND stock_quantity>=?";

                try (PreparedStatement insert =
                             con.prepareStatement(itemSql);
                     PreparedStatement stock =
                             con.prepareStatement(stockSql)) {

                    for (Line line : lines) {

                        insert.setLong(1, orderId);
                        insert.setInt(2, line.productId);
                        insert.setInt(3, line.quantity);
                        insert.setBigDecimal(4, line.price);

                        if (line.sellerId == null) {

                            insert.setNull(5, Types.INTEGER);
                            insert.setNull(6, Types.BIGINT);

                        } else {

                            insert.setInt(5, line.sellerId);
                            insert.setLong(
                                    6,
                                    subOrderIds.get(line.sellerId)
                            );
                        }

                        insert.addBatch();

                        stock.setInt(1, line.quantity);
                        stock.setInt(2, line.productId);
                        stock.setInt(3, line.quantity);

                        stock.addBatch();
                    }

                    insert.executeBatch();

                    int[] stockResults = stock.executeBatch();

                    for (int result : stockResults) {
                        if (result == 0) {
                            throw new SQLException(
                                    "Unable to update product stock."
                            );
                        }
                    }
                }

                // Save payment information.
                String paymentSql =
                        "INSERT INTO payments("
                        + "order_id,provider,amount,status,"
                        + "transaction_reference"
                        + ") VALUES(?,?,?,?,?)";

                try (PreparedStatement ps =
                             con.prepareStatement(paymentSql)) {

                    ps.setLong(1, orderId);

                    ps.setString(
                            2,
                            "COD".equals(payment)
                            ? "CASH_ON_DELIVERY"
                            : "SIMULATED_TEST_GATEWAY"
                    );

                    ps.setBigDecimal(3, total);
                    ps.setString(4, paymentStatus);

                    ps.setString(
                            5,
                            "TEST-" + UUID.randomUUID()
                                    .toString()
                                    .substring(0, 12)
                                    .toUpperCase()
                    );

                    ps.executeUpdate();
                }

                // Save customer delivery address if requested.
                if (body.has("saveAddress")
                        && body.get("saveAddress").getAsBoolean()) {

                    String addressSql =
                            "INSERT INTO customer_addresses("
                            + "customer_id,label,recipient_name,phone,"
                            + "address_line,city,state,pincode,is_default"
                            + ") VALUES(?,?,?,?,?,?,?,?,0)";

                    try (PreparedStatement ps =
                                 con.prepareStatement(addressSql)) {

                        ps.setInt(1, customerId);

                        ps.setString(
                                2,
                                ApiUtil.string(
                                        address,
                                        "label",
                                        "Delivery"
                                )
                        );

                        ps.setString(3, recipient);
                        ps.setString(4, phone);
                        ps.setString(5, addressLine);
                        ps.setString(6, city);
                        ps.setString(7, state);
                        ps.setString(8, pincode);

                        ps.executeUpdate();
                    }
                }

                // Commit order before sending emails.
                con.commit();

                if (session != null) {
                    session.removeAttribute("cart");
                }

                // Customer confirmation email.
                sendEmailSafely(
                        email,
                        "Handcraft Shop order confirmed - " + orderNumber,
                        "Thank you, " + recipient
                        + ". Your order total is ₹" + total
                        + ". Payment status: " + paymentStatus
                        + ". Delivery: " + addressLine
                        + ", " + city + ", " + state
                        + " - " + pincode + "."
                );

                // Notify sellers.
                for (Integer sellerId : sellerTotals.keySet()) {

                    String sellerMailSql =
                            "SELECT email,shop_name "
                            + "FROM sellers WHERE seller_id=?";

                    try (PreparedStatement sellerMail =
                                 con.prepareStatement(sellerMailSql)) {

                        sellerMail.setInt(1, sellerId);

                        try (ResultSet sellerRow =
                                     sellerMail.executeQuery()) {

                            if (sellerRow.next()) {

                                sendEmailSafely(
                                        sellerRow.getString("email"),
                                        "New order for "
                                        + sellerRow.getString("shop_name"),
                                        "A customer placed order "
                                        + orderNumber
                                        + " containing items from your shop. "
                                        + "Sign in to Seller Studio to review "
                                        + "and fulfil your seller order."
                                );
                            }
                        }
                    } catch (Exception mailError) {
                        // Do not undo an already-committed order.
                        mailError.printStackTrace();
                    }
                }

                // Send successful order response to checkout.html.
                Map<String, Object> result =
                        new LinkedHashMap<>();

                result.put("success", true);
                result.put("orderId", orderId);
                result.put("orderNumber", orderNumber);
                result.put("total", total);
                result.put("paymentStatus", paymentStatus);
                result.put(
                        "message",
                        "Your order has been placed successfully."
                );

                ApiUtil.ok(resp, result);

            } catch (Exception e) {

                con.rollback();

                e.printStackTrace();

                ApiUtil.error(
                        resp,
                        400,
                        e.getMessage() == null
                        ? "Unable to place your order."
                        : e.getMessage()
                );

            } finally {
                con.setAutoCommit(true);
            }

        } catch (SQLException e) {

            e.printStackTrace();

            ApiUtil.error(
                    resp,
                    500,
                    "Database error: " + e.getMessage()
            );
        }
    }

    // Email errors must not make an already-placed order fail.
    private void sendEmailSafely(
            String email,
            String subject,
            String message
    ) {

        if (email == null || email.isBlank()) {
            return;
        }

        try {
            EmailService.send(email, subject, message);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void cancel(
            JsonObject body,
            HttpServletRequest req,
            HttpServletResponse resp
    ) throws IOException {

        if (!ApiUtil.requireRole(req, resp, "customer")) {
            return;
        }

        if (body == null || !body.has("orderId")) {
            ApiUtil.error(resp, 400, "Invalid order ID.");
            return;
        }

        long orderId = body.get("orderId").getAsLong();

        String reason = ApiUtil.string(
                body,
                "reason",
                "Customer requested cancellation."
        );

        try (Connection c = DBConnection.getConnection()) {

            c.setAutoCommit(false);

            try {

                String email = null;
                String orderNumber = "";

                String selectSql =
                        "SELECT guest_email,order_number,status "
                        + "FROM orders "
                        + "WHERE id=? AND customer_id=? FOR UPDATE";

                try (PreparedStatement p =
                             c.prepareStatement(selectSql)) {

                    p.setLong(1, orderId);
                    p.setInt(2, ApiUtil.sessionId(req));

                    try (ResultSet r = p.executeQuery()) {

                        if (!r.next()) {
                            throw new SQLException("Order not found.");
                        }

                        String status = r.getString("status");

                        email = r.getString("guest_email");
                        orderNumber = r.getString("order_number");

                        if (!Set.of("PLACED", "CONFIRMED")
                                .contains(status)) {

                            throw new SQLException(
                                    "This order can no longer be cancelled "
                                    + "because it is being processed or shipped."
                            );
                        }
                    }
                }

                // Restore product stock.
                String stockSql =
                        "UPDATE products p "
                        + "JOIN order_items oi "
                        + "ON oi.product_id=p.id "
                        + "SET p.stock_quantity="
                        + "p.stock_quantity+oi.quantity "
                        + "WHERE oi.order_id=? "
                        + "AND oi.item_status<>'CANCELLED'";

                try (PreparedStatement p =
                             c.prepareStatement(stockSql)) {

                    p.setLong(1, orderId);
                    p.executeUpdate();
                }

                // Update main order.
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE orders SET status='CANCELLED',"
                        + "cancel_reason=? WHERE id=?"
                )) {

                    p.setString(1, reason);
                    p.setLong(2, orderId);
                    p.executeUpdate();
                }

                // Update seller orders.
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE seller_orders "
                        + "SET status='CANCELLED',updated_at=NOW() "
                        + "WHERE order_id=?"
                )) {

                    p.setLong(1, orderId);
                    p.executeUpdate();
                }

                // Update order items.
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE order_items "
                        + "SET item_status='CANCELLED' "
                        + "WHERE order_id=?"
                )) {

                    p.setLong(1, orderId);
                    p.executeUpdate();
                }

                // Update payment status.
                try (PreparedStatement p = c.prepareStatement(
                        "UPDATE payments "
                        + "SET status='CANCELLED' WHERE order_id=?"
                )) {

                    p.setLong(1, orderId);
                    p.executeUpdate();
                }

                c.commit();

                sendEmailSafely(
                        email,
                        "Handcraft Shop order cancelled - " + orderNumber,
                        "Your order " + orderNumber
                        + " has been cancelled. Reason: " + reason
                );

                ApiUtil.ok(
                        resp,
                        Map.of(
                                "success", true,
                                "message", "Order cancelled."
                        )
                );

            } catch (Exception e) {

                c.rollback();

                ApiUtil.error(
                        resp,
                        400,
                        e.getMessage() == null
                        ? "Unable to cancel order."
                        : e.getMessage()
                );
            }

        } catch (SQLException e) {

            e.printStackTrace();

            ApiUtil.error(resp, 500, e.getMessage());
        }
    }

    private static class Line {

        int productId;
        int quantity;

        BigDecimal price;

        Integer sellerId;

        Line(
                int productId,
                int quantity,
                BigDecimal price,
                Integer sellerId
        ) {

            this.productId = productId;
            this.quantity = quantity;
            this.price = price;
            this.sellerId = sellerId;
        }
    }
}
