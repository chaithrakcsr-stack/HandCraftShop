package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.util.ApiUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/categories")
public class CategoryServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        List<Map<String, Object>> items = new ArrayList<>();

        String sql =
            "SELECT sc.category_id, sc.category_name, sc.description, " +
            "sc.image_url, COUNT(p.id) AS product_count " +
            "FROM storefront_categories sc " +
            "LEFT JOIN storefront_category_products scp " +
            "ON scp.category_id = sc.category_id " +
            "LEFT JOIN products p " +
            "ON p.id = scp.product_id AND p.active = 1 " +
            "WHERE sc.active = 1 " +
            "GROUP BY sc.category_id, sc.category_name, " +
            "sc.description, sc.image_url, sc.display_order " +
            "ORDER BY sc.display_order";

        try (
            Connection c = DBConnection.getConnection();
            PreparedStatement p = c.prepareStatement(sql);
            ResultSet r = p.executeQuery()
        ) {

            while (r.next()) {

                Map<String, Object> m = new LinkedHashMap<>();

                m.put("id", r.getInt("category_id"));
                m.put("name", r.getString("category_name"));
                m.put("description", r.getString("description"));
                m.put("imageUrl", r.getString("image_url"));
                m.put("productCount", r.getInt("product_count"));

                items.add(m);
            }

            ApiUtil.ok(resp, items);

        } catch (SQLException e) {
            ApiUtil.error(resp, 500, e.getMessage());
        }
    }
}