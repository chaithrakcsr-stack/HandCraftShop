package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.util.ApiUtil;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.math.BigDecimal;

@WebServlet("/api/products")
public class ProductServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        List<Map<String, Object>> products = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
            "SELECT p.id, p.name, sc.category_name AS category, " +
            "p.origin, p.artisan, p.price_inr, p.price_usd, " +
            "p.dimensions, p.badge, p.description, p.material, " +
            "p.stock_quantity, p.rating_avg, p.rating_count, p.seller_id, " +
            "COALESCE(pi.image_path, '') AS image_url, " +
            "(SELECT GROUP_CONCAT(pi2.image_path ORDER BY pi2.sort_order SEPARATOR '|#|') " +
            " FROM product_images pi2 WHERE pi2.product_id = p.id) AS image_list, " +
            "COALESCE(s.shop_name, '') AS shop_name, " +
            "COALESCE(s.seller_location, p.origin) AS seller_location " +
            "FROM storefront_category_products scp " +
            "JOIN storefront_categories sc ON sc.category_id = scp.category_id " +
            "JOIN products p ON p.id = scp.product_id " +
            "LEFT JOIN sellers s ON s.seller_id = p.seller_id " +
            "LEFT JOIN product_images pi ON pi.product_id = p.id AND pi.is_primary = 1 " +
            "WHERE p.active = 1 AND sc.active = 1"
        );

        List<Object> args = new ArrayList<>();

        String category = request.getParameter("category");
        String q = request.getParameter("q");
        String material = request.getParameter("material");
        String min = request.getParameter("minPrice");
        String max = request.getParameter("maxPrice");
        String minRating = request.getParameter("minRating");
        String sort = request.getParameter("sort");

        if (category != null && !category.isBlank()
                && !"All".equalsIgnoreCase(category)) {
            sql.append(" AND sc.category_name = ?");
            args.add(category);
        }

        if (q != null && !q.isBlank()) {
            sql.append(
                " AND (p.name LIKE ? OR sc.category_name LIKE ? " +
                "OR p.category LIKE ? OR p.origin LIKE ? " +
                "OR p.artisan LIKE ? OR p.description LIKE ? OR p.material LIKE ?)"
            );

            String like = "%" + q.trim() + "%";

            for (int i = 0; i < 7; i++) {
                args.add(like);
            }
        }

        if (material != null && !material.isBlank()) {
            sql.append(" AND p.material LIKE ?");
            args.add("%" + material.trim() + "%");
        }

        try {
            if (min != null && !min.isBlank()) {
                sql.append(" AND p.price_inr >= ?");
                args.add(new BigDecimal(min));
            }

            if (max != null && !max.isBlank()) {
                sql.append(" AND p.price_inr <= ?");
                args.add(new BigDecimal(max));
            }

            if (minRating != null && !minRating.isBlank()) {
                sql.append(" AND p.rating_avg >= ?");
                args.add(new BigDecimal(minRating));
            }

        } catch (NumberFormatException e) {
            ApiUtil.error(response, 400,
                    "Price and rating filters must be valid numbers.");
            return;
        }

        if ("price-asc".equals(sort)) {
            sql.append(" ORDER BY p.price_inr ASC");
        } else if ("price-desc".equals(sort)) {
            sql.append(" ORDER BY p.price_inr DESC");
        } else if ("rating".equals(sort)) {
            sql.append(" ORDER BY p.rating_avg DESC, p.rating_count DESC");
        } else {
            sql.append(" ORDER BY p.featured DESC, sc.display_order ASC, scp.display_order ASC");
        }

        try (
            Connection con = DBConnection.getConnection();
            PreparedStatement ps = con.prepareStatement(sql.toString())
        ) {

            for (int i = 0; i < args.size(); i++) {
                ps.setObject(i + 1, args.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {

                    Map<String, Object> p = new LinkedHashMap<>();

                    p.put("id", rs.getInt("id"));
                    p.put("name", rs.getString("name"));
                    p.put("category", rs.getString("category"));
                    p.put("origin", rs.getString("origin"));
                    p.put("artisan", rs.getString("artisan"));

                    p.put("priceINR", rs.getBigDecimal("price_inr"));
                    p.put("priceUSD", rs.getBigDecimal("price_usd"));

                    p.put("dimensions", rs.getString("dimensions"));
                    p.put("badge", rs.getString("badge"));
                    p.put("description", rs.getString("description"));
                    p.put("material", rs.getString("material"));

                    p.put("stockQuantity", rs.getInt("stock_quantity"));
                    p.put("ratingAverage", rs.getBigDecimal("rating_avg"));
                    p.put("ratingCount", rs.getInt("rating_count"));

                    p.put("sellerId", rs.getObject("seller_id"));
                    p.put("imageUrl", rs.getString("image_url"));

                    String imageList = rs.getString("image_list");

                    p.put(
                        "images",
                        imageList == null || imageList.isBlank()
                            ? List.of()
                            : Arrays.asList(imageList.split("\\|#\\|"))
                    );

                    p.put("shopName", rs.getString("shop_name"));
                    p.put("sellerLocation", rs.getString("seller_location"));

                    products.add(p);
                }
            }

            ApiUtil.ok(response, products);

        } catch (SQLException e) {
            ApiUtil.error(response, 500,
                    "Database error: " + e.getMessage());
        }
    }
}