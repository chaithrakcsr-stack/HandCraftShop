package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.util.ApiUtil;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/wishlist")
public class WishlistServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp) throws IOException {
        if(!ApiUtil.requireRole(req,resp,"customer"))return;List<Map<String,Object>> rows=new ArrayList<>();
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT p.id,p.name,p.category,p.origin,p.price_inr,pi.image_path AS image_url FROM wishlist_items w JOIN products p ON p.id=w.product_id LEFT JOIN product_images pi ON pi.product_id=p.id AND pi.is_primary=1 WHERE w.customer_id=? ORDER BY w.created_at DESC")){
            p.setInt(1,ApiUtil.sessionId(req));try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> m=new LinkedHashMap<>();m.put("id",r.getInt("id"));m.put("name",r.getString("name"));m.put("category",r.getString("category"));m.put("origin",r.getString("origin"));m.put("priceINR",r.getBigDecimal("price_inr"));m.put("imageUrl",r.getString("image_url"));rows.add(m);}}ApiUtil.ok(resp,rows);
        }catch(SQLException e){ApiUtil.error(resp,500,e.getMessage());}
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp) throws IOException {
        if(!ApiUtil.requireRole(req,resp,"customer"))return;JsonObject b=ApiUtil.readJson(req);
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("INSERT IGNORE INTO wishlist_items(customer_id,product_id) VALUES(?,?)")){p.setInt(1,ApiUtil.sessionId(req));p.setInt(2,b.get("productId").getAsInt());p.executeUpdate();ApiUtil.ok(resp,Map.of("success",true,"message","Saved to wishlist."));}catch(Exception e){ApiUtil.error(resp,400,e.getMessage());}
    }
    @Override protected void doDelete(HttpServletRequest req,HttpServletResponse resp) throws IOException {
        if(!ApiUtil.requireRole(req,resp,"customer"))return;try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM wishlist_items WHERE customer_id=? AND product_id=?")){p.setInt(1,ApiUtil.sessionId(req));p.setInt(2,Integer.parseInt(req.getParameter("productId")));p.executeUpdate();ApiUtil.ok(resp,Map.of("success",true));}catch(Exception e){ApiUtil.error(resp,400,e.getMessage());}
    }
}
