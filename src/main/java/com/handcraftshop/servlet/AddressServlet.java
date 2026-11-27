package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.util.ApiUtil;
import com.google.gson.JsonObject;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.*;

@WebServlet("/api/addresses")
public class AddressServlet extends HttpServlet {
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp) throws IOException {
        if (!ApiUtil.requireRole(req,resp,"customer")) return;
        List<Map<String,Object>> rows=new ArrayList<>();
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT address_id,label,recipient_name,phone,address_line,city,state,pincode,is_default FROM customer_addresses WHERE customer_id=? ORDER BY is_default DESC,address_id DESC")){
            p.setInt(1,ApiUtil.sessionId(req));try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> m=new LinkedHashMap<>();for(String k:List.of("address_id","label","recipient_name","phone","address_line","city","state","pincode","is_default"))m.put(k,r.getObject(k));rows.add(m);}}
            ApiUtil.ok(resp,rows);
        }catch(SQLException e){ApiUtil.error(resp,500,e.getMessage());}
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp) throws IOException {
        if (!ApiUtil.requireRole(req,resp,"customer")) return; JsonObject b=ApiUtil.readJson(req);
        try(Connection c=DBConnection.getConnection()){
            c.setAutoCommit(false);
            if(b.has("isDefault")&&b.get("isDefault").getAsBoolean())try(PreparedStatement p=c.prepareStatement("UPDATE customer_addresses SET is_default=0 WHERE customer_id=?")){p.setInt(1,ApiUtil.sessionId(req));p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("INSERT INTO customer_addresses(customer_id,label,recipient_name,phone,address_line,city,state,pincode,is_default) VALUES(?,?,?,?,?,?,?,?,?)",Statement.RETURN_GENERATED_KEYS)){
                p.setInt(1,ApiUtil.sessionId(req));p.setString(2,ApiUtil.string(b,"label","Home"));p.setString(3,ApiUtil.string(b,"recipientName",""));p.setString(4,ApiUtil.string(b,"phone",""));p.setString(5,ApiUtil.string(b,"addressLine",""));p.setString(6,ApiUtil.string(b,"city",""));p.setString(7,ApiUtil.string(b,"state",""));p.setString(8,ApiUtil.string(b,"pincode",""));p.setBoolean(9,b.has("isDefault")&&b.get("isDefault").getAsBoolean());p.executeUpdate();
                try(ResultSet r=p.getGeneratedKeys()){r.next();c.commit();ApiUtil.send(resp,201,Map.of("success",true,"addressId",r.getLong(1)));}
            }catch(Exception e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }catch(Exception e){ApiUtil.error(resp,500,e.getMessage());}
    }
    @Override protected void doPut(HttpServletRequest req,HttpServletResponse resp) throws IOException {
        if (!ApiUtil.requireRole(req,resp,"customer")) return;
        JsonObject b=ApiUtil.readJson(req);long id=b.has("addressId")?b.get("addressId").getAsLong():0;
        try(Connection c=DBConnection.getConnection()){
            c.setAutoCommit(false);
            if(b.has("isDefault")&&b.get("isDefault").getAsBoolean())try(PreparedStatement p=c.prepareStatement("UPDATE customer_addresses SET is_default=0 WHERE customer_id=?")){p.setInt(1,ApiUtil.sessionId(req));p.executeUpdate();}
            try(PreparedStatement p=c.prepareStatement("UPDATE customer_addresses SET label=?,recipient_name=?,phone=?,address_line=?,city=?,state=?,pincode=?,is_default=? WHERE address_id=? AND customer_id=?")){
                p.setString(1,ApiUtil.string(b,"label","Home"));p.setString(2,ApiUtil.string(b,"recipientName",""));p.setString(3,ApiUtil.string(b,"phone",""));p.setString(4,ApiUtil.string(b,"addressLine",""));p.setString(5,ApiUtil.string(b,"city",""));p.setString(6,ApiUtil.string(b,"state",""));p.setString(7,ApiUtil.string(b,"pincode",""));p.setBoolean(8,b.has("isDefault")&&b.get("isDefault").getAsBoolean());p.setLong(9,id);p.setInt(10,ApiUtil.sessionId(req));
                if(p.executeUpdate()==0){c.rollback();ApiUtil.error(resp,404,"Address not found.");return;}
            }
            c.commit();ApiUtil.ok(resp,Map.of("success",true,"message","Address updated."));
        }catch(SQLException e){ApiUtil.error(resp,500,e.getMessage());}
    }
    @Override protected void doDelete(HttpServletRequest req,HttpServletResponse resp) throws IOException {
        if (!ApiUtil.requireRole(req,resp,"customer")) return;String id=req.getParameter("id");
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("DELETE FROM customer_addresses WHERE address_id=? AND customer_id=?")){p.setLong(1,Long.parseLong(id));p.setInt(2,ApiUtil.sessionId(req));p.executeUpdate();ApiUtil.ok(resp,Map.of("success",true));}catch(Exception e){ApiUtil.error(resp,400,e.getMessage());}
    }
}
