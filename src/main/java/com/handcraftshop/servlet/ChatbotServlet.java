package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.util.ApiUtil;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/chatbot")
public class ChatbotServlet extends HttpServlet {
    private List<String> categories() throws SQLException {
        List<String> out=new ArrayList<>();try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT name FROM categories ORDER BY sort_order,name");ResultSet r=p.executeQuery()){while(r.next())out.add(r.getString(1));}return out;
    }
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException{try{ApiUtil.ok(resp,Map.of("message","I can help you explore every craft category, find products, understand orders, shipping, returns and seller registration.","categories",categories()));}catch(SQLException e){ApiUtil.error(resp,500,e.getMessage());}}
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        JsonObject b=ApiUtil.readJson(req);String q=ApiUtil.string(b,"message","").toLowerCase(Locale.ROOT);String answer;
        try{
            List<String> cats=categories();String matched=null;for(String c:cats)if(q.contains(c.toLowerCase(Locale.ROOT))){matched=c;break;}
            if(matched!=null)answer="Explore "+matched+" in the Collection section. You can search by product name, material, price and artisan origin. Sellers can upload multiple photos for their listings.";
            else if(q.contains("category")||q.contains("craft"))answer="Our marketplace includes "+String.join(", ",cats)+". Tell me a category or material and I can help you find it.";
            else if(q.contains("order")||q.contains("track"))answer="Sign in as a customer and open My Account to view order history and order status. Orders can be cancelled while they are still eligible for cancellation.";
            else if(q.contains("ship")||q.contains("deliver")||q.contains("location"))answer="During checkout, enter your delivery address, city, state and PIN code. Each artisan listing also shows its place of origin or seller location.";
            else if(q.contains("return")||q.contains("cancel"))answer="You can request cancellation from My Account while the order has not been shipped. Contact the marketplace team for assistance with a delivered item.";
            else if(q.contains("seller")||q.contains("sell"))answer="Choose Seller Registration, enter your shop details and location, then wait for admin approval before publishing products.";
            else if(q.contains("payment")||q.contains("pay"))answer="Checkout currently supports cash on delivery and a clearly labelled simulated test-payment mode. Test mode does not charge real money.";
            else if(q.contains("review")||q.contains("rating"))answer="Customers can leave a 1–5 star rating and a written review for a product from the product detail or account area.";
            else answer="I can help with product categories, materials, seller registration, shipping locations, orders, cancellations, payments and reviews. What would you like to explore?";
            ApiUtil.ok(resp,Map.of("answer",answer,"categories",cats));
        }catch(SQLException e){ApiUtil.error(resp,500,e.getMessage());}
    }
}
