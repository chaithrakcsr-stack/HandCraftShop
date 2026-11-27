package com.handcraftshop.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;

@WebServlet("/api/cart")
public class CartServlet extends HttpServlet {
    private final Gson gson = new Gson();
    @SuppressWarnings("unchecked") private Map<Integer,Integer> cart(HttpServletRequest request) {
        HttpSession session=request.getSession(true);Map<Integer,Integer> cart=(Map<Integer,Integer>)session.getAttribute("cart");
        if(cart==null){cart=new LinkedHashMap<>();session.setAttribute("cart",cart);}return cart;
    }
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws IOException{
        response.setContentType("application/json");List<Map<String,Integer>> items=new ArrayList<>();cart(request).forEach((id,qty)->items.add(Map.of("id",id,"qty",qty)));
        response.getWriter().write(gson.toJson(Map.of("items",items,"count",cart(request).values().stream().mapToInt(Integer::intValue).sum())));
    }
    @Override protected void doPost(HttpServletRequest request,HttpServletResponse response)throws IOException{
        JsonObject b=gson.fromJson(request.getReader(),JsonObject.class);if(b==null||!b.has("productId")){response.sendError(400,"productId required");return;}
        int id=b.get("productId").getAsInt(),qty=b.has("quantity")?b.get("quantity").getAsInt():1;Map<Integer,Integer> cart=cart(request);
        cart.put(id,Math.min(99,cart.getOrDefault(id,0)+Math.max(1,qty)));writeCart(response,cart);
    }
    @Override protected void doPut(HttpServletRequest request,HttpServletResponse response)throws IOException{
        JsonObject b=gson.fromJson(request.getReader(),JsonObject.class);int id=b.get("productId").getAsInt(),qty=b.get("quantity").getAsInt();Map<Integer,Integer> cart=cart(request);
        if(qty<=0)cart.remove(id);else cart.put(id,Math.min(qty,99));writeCart(response,cart);
    }
    @Override protected void doDelete(HttpServletRequest request,HttpServletResponse response)throws IOException{
        String raw=request.getReader().lines().reduce("",(a,b)->a+b);Map<Integer,Integer> cart=cart(request);
        if(raw.isBlank())cart.clear();else{JsonObject b=gson.fromJson(raw,JsonObject.class);if(b.has("productId"))cart.remove(b.get("productId").getAsInt());else cart.clear();}writeCart(response,cart);
    }
    private void writeCart(HttpServletResponse response,Map<Integer,Integer> cart)throws IOException{response.setContentType("application/json");response.setCharacterEncoding("UTF-8");response.getWriter().write(gson.toJson(Map.of("success",true,"count",cart.values().stream().mapToInt(Integer::intValue).sum())));}
}
