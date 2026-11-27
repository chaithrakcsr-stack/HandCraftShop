package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.util.ApiUtil;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/reviews")
public class ReviewServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        String id=req.getParameter("productId");if(id==null){ApiUtil.error(resp,400,"productId is required.");return;}List<Map<String,Object>> rows=new ArrayList<>();
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT r.rating,r.review_text,r.created_at,c.full_name FROM product_reviews r JOIN customers c ON c.customer_id=r.customer_id WHERE r.product_id=? AND r.is_visible=1 ORDER BY r.created_at DESC")){p.setInt(1,Integer.parseInt(id));try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> m=new LinkedHashMap<>();m.put("rating",r.getInt("rating"));m.put("review",r.getString("review_text"));m.put("createdAt",r.getTimestamp("created_at"));m.put("customerName",r.getString("full_name"));rows.add(m);}}ApiUtil.ok(resp,rows);}catch(Exception e){ApiUtil.error(resp,400,e.getMessage());}
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        if(!ApiUtil.requireRole(req,resp,"customer"))return;JsonObject b=ApiUtil.readJson(req);int product=ApiUtil.integer(b,"productId",0),rating=ApiUtil.integer(b,"rating",0);String review=ApiUtil.string(b,"review","");if(product<1||rating<1||rating>5||review.isBlank()){ApiUtil.error(resp,400,"Choose a product, rating from 1 to 5 and enter a review.");return;}
        try(Connection c=DBConnection.getConnection()){c.setAutoCommit(false);try(PreparedStatement p=c.prepareStatement("INSERT INTO product_reviews(product_id,customer_id,rating,review_text) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE rating=VALUES(rating),review_text=VALUES(review_text),created_at=CURRENT_TIMESTAMP")){p.setInt(1,product);p.setInt(2,ApiUtil.sessionId(req));p.setInt(3,rating);p.setString(4,review);p.executeUpdate();}try(PreparedStatement p=c.prepareStatement("UPDATE products SET rating_avg=(SELECT AVG(rating) FROM product_reviews WHERE product_id=?),rating_count=(SELECT COUNT(*) FROM product_reviews WHERE product_id=?) WHERE id=?")){p.setInt(1,product);p.setInt(2,product);p.setInt(3,product);p.executeUpdate();}c.commit();ApiUtil.ok(resp,Map.of("success",true,"message","Your review has been saved."));}catch(SQLException e){ApiUtil.error(resp,500,e.getMessage());}
    }
}
