package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.util.ApiUtil;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/profile")
public class ProfileServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String role = ApiUtil.role(req); Integer id = ApiUtil.sessionId(req);
        if (id == null || !"customer".equals(role)) { ApiUtil.error(resp, 401, "Customer sign in required."); return; }
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT customer_id,full_name,email,phone,address_line,city,state,pincode FROM customers WHERE customer_id=?")) {
            ps.setInt(1, id); try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) { ApiUtil.error(resp, 404, "Customer profile not found."); return; }
                Map<String,Object> m = new LinkedHashMap<>();
                for (String c : List.of("customer_id","full_name","email","phone","address_line","city","state","pincode")) m.put(c, rs.getObject(c));
                ApiUtil.ok(resp, m);
            }
        } catch (SQLException e) { ApiUtil.error(resp, 500, e.getMessage()); }
    }
    @Override protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (!ApiUtil.requireRole(req, resp, "customer")) return;
        JsonObject b = ApiUtil.readJson(req);
        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement("UPDATE customers SET full_name=?,phone=?,address_line=?,city=?,state=?,pincode=?,updated_at=NOW() WHERE customer_id=?")) {
            ps.setString(1, ApiUtil.string(b,"fullName","")); ps.setString(2, ApiUtil.string(b,"phone",""));
            ps.setString(3, ApiUtil.string(b,"addressLine","")); ps.setString(4, ApiUtil.string(b,"city",""));
            ps.setString(5, ApiUtil.string(b,"state","")); ps.setString(6, ApiUtil.string(b,"pincode","")); ps.setInt(7, ApiUtil.sessionId(req));
            ps.executeUpdate(); ApiUtil.ok(resp, Map.of("success",true,"message","Profile updated."));
        } catch (SQLException e) { ApiUtil.error(resp,500,e.getMessage()); }
    }
}
