package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.util.ApiUtil;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

@WebServlet("/api/seller/products")
@MultipartConfig(fileSizeThreshold=1024*1024,maxFileSize=8*1024*1024,maxRequestSize=32*1024*1024)
public class SellerProductServlet extends HttpServlet {
    private boolean approved(HttpServletRequest req,HttpServletResponse resp)throws IOException,SQLException{
        if(!ApiUtil.requireRole(req,resp,"seller"))return false;
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT approval_status FROM sellers WHERE seller_id=?")){p.setInt(1,ApiUtil.sessionId(req));try(ResultSet r=p.executeQuery()){if(!r.next()||!"APPROVED".equalsIgnoreCase(r.getString(1))){ApiUtil.error(resp,403,"Your seller account must be approved before you can manage products.");return false;}}}return true;
    }
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        if(!ApiUtil.requireRole(req,resp,"seller"))return;List<Map<String,Object>> rows=new ArrayList<>();
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("SELECT p.id,p.name,p.category,p.material,p.price_inr,p.stock_quantity,p.active,p.origin,p.description,(SELECT image_path FROM product_images i WHERE i.product_id=p.id AND i.is_primary=1 LIMIT 1) AS image_url FROM products p WHERE p.seller_id=? ORDER BY p.id DESC")){p.setInt(1,ApiUtil.sessionId(req));try(ResultSet r=p.executeQuery()){while(r.next()){Map<String,Object> m=new LinkedHashMap<>();for(String k:List.of("id","name","category","material","price_inr","stock_quantity","active","origin","description","image_url"))m.put(k,r.getObject(k));rows.add(m);}}ApiUtil.ok(resp,rows);}catch(SQLException e){ApiUtil.error(resp,500,e.getMessage());}
    }
    @Override protected void doPost(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        try{if(!approved(req,resp))return;String name=field(req,"name"),category=field(req,"category"),price=field(req,"priceINR"),origin=field(req,"origin"),description=field(req,"description");
            if(name.isBlank()||category.isBlank()||price.isBlank()){ApiUtil.error(resp,400,"Product name, category and price are required.");return;}
            int stock=parseInt(field(req,"stockQuantity"),1);if(stock<0){ApiUtil.error(resp,400,"Stock cannot be negative.");return;}
            java.math.BigDecimal priceInr;
            try { priceInr = new java.math.BigDecimal(price); }
            catch (NumberFormatException e) { ApiUtil.error(resp,400,"Enter a valid product price."); return; }
            if(priceInr.compareTo(java.math.BigDecimal.ZERO)<=0){ApiUtil.error(resp,400,"Product price must be greater than zero.");return;}
            java.math.BigDecimal priceUsd=priceInr.divide(new java.math.BigDecimal("80"),2,java.math.RoundingMode.HALF_UP);
            try(Connection c=DBConnection.getConnection()){
                c.setAutoCommit(false);int productId;
                try(PreparedStatement p=c.prepareStatement("INSERT INTO products(name,category,origin,artisan,price_inr,price_usd,dimensions,badge,description,active,seller_id,material,stock_quantity,featured) VALUES(?,?,?,?,?,?,?,?,?,1,?,?,?,0)",Statement.RETURN_GENERATED_KEYS)){
                    p.setString(1,name);p.setString(2,category);p.setString(3,origin);p.setString(4,field(req,"artisan"));p.setBigDecimal(5,priceInr);p.setBigDecimal(6,priceUsd);p.setString(7,field(req,"dimensions"));p.setString(8,field(req,"badge"));p.setString(9,description);p.setInt(10,ApiUtil.sessionId(req));p.setString(11,field(req,"material"));p.setInt(12,stock);p.executeUpdate();try(ResultSet k=p.getGeneratedKeys()){k.next();productId=k.getInt(1);}}
                List<String> images=saveImages(req,productId);if(images.isEmpty())throw new IOException("Upload at least one product photo so customers can see your handmade item.");
                try(PreparedStatement p=c.prepareStatement("INSERT INTO product_images(product_id,image_path,is_primary,sort_order) VALUES(?,?,?,?)")){for(int i=0;i<images.size();i++){p.setInt(1,productId);p.setString(2,images.get(i));p.setBoolean(3,i==0);p.setInt(4,i);p.addBatch();}p.executeBatch();}
                c.commit();ApiUtil.send(resp,201,Map.of("success",true,"productId",productId,"images",images,"message","Product published to the marketplace."));
            }catch(Exception e){ApiUtil.error(resp,500,e.getMessage());}
        }catch(SQLException e){ApiUtil.error(resp,500,e.getMessage());}
    }
    @Override protected void doPut(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        try { if(!approved(req,resp)) return; } catch(SQLException e) { ApiUtil.error(resp,500,e.getMessage()); return; }
        JsonObject b=ApiUtil.readJson(req);int id=ApiUtil.integer(b,"productId",0);
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("UPDATE products SET name=?,category=?,material=?,origin=?,description=?,price_inr=?,price_usd=?,stock_quantity=? WHERE id=? AND seller_id=?")){
            String name=ApiUtil.string(b,"name","");String category=ApiUtil.string(b,"category","");java.math.BigDecimal price=new java.math.BigDecimal(ApiUtil.string(b,"priceINR","0"));int stock=ApiUtil.integer(b,"stockQuantity",0);
            if(name.isBlank()||category.isBlank()||price.compareTo(java.math.BigDecimal.ZERO)<=0||stock<0){ApiUtil.error(resp,400,"Enter a name, category, positive price and non-negative stock.");return;}
            p.setString(1,name);p.setString(2,category);p.setString(3,ApiUtil.string(b,"material",""));p.setString(4,ApiUtil.string(b,"origin",""));p.setString(5,ApiUtil.string(b,"description",""));p.setBigDecimal(6,price);p.setBigDecimal(7,price.divide(new java.math.BigDecimal("80"),2,java.math.RoundingMode.HALF_UP));p.setInt(8,stock);p.setInt(9,id);p.setInt(10,ApiUtil.sessionId(req));if(p.executeUpdate()==0){ApiUtil.error(resp,404,"Product not found or not owned by this seller.");return;}ApiUtil.ok(resp,Map.of("success",true,"message","Product updated."));
        }catch(Exception e){ApiUtil.error(resp,400,e.getMessage());}
    }
    @Override protected void doDelete(HttpServletRequest req,HttpServletResponse resp)throws IOException{
        try { if(!approved(req,resp)) return; } catch(SQLException e) { ApiUtil.error(resp,500,e.getMessage()); return; }
        try(Connection c=DBConnection.getConnection();PreparedStatement p=c.prepareStatement("UPDATE products SET active=0 WHERE id=? AND seller_id=?")){p.setInt(1,Integer.parseInt(req.getParameter("id")));p.setInt(2,ApiUtil.sessionId(req));p.executeUpdate();ApiUtil.ok(resp,Map.of("success",true,"message","Product removed from the marketplace."));}catch(Exception e){ApiUtil.error(resp,400,e.getMessage());}
    }
    private String field(HttpServletRequest req,String n){String v=req.getParameter(n);return v==null?"":v.trim();}
    private int parseInt(String v,int d){try{return Integer.parseInt(v);}catch(Exception e){return d;}}
    private List<String> saveImages(HttpServletRequest req,int productId)throws IOException,ServletException{
        List<String> paths=new ArrayList<>();String real=req.getServletContext().getRealPath("/uploads");Path folder=real==null?Paths.get(System.getProperty("catalina.base","."),"webapps","HandcraftShop","uploads"):Paths.get(real);Files.createDirectories(folder);
        for(Part part:req.getParts()){
            if(!"images".equals(part.getName())||part.getSize()==0)continue;
            if(paths.size()>=4)throw new IOException("You can upload a maximum of 4 product photos at a time.");
            String submitted=part.getSubmittedFileName();
            String ext=(submitted!=null&&submitted.lastIndexOf('.')>=0)?submitted.substring(submitted.lastIndexOf('.')).toLowerCase(Locale.ROOT):"";
            if(!List.of(".jpg",".jpeg",".png",".webp").contains(ext))throw new IOException("Only JPG, JPEG, PNG and WebP images are allowed.");
            String filename="product-"+productId+"-"+UUID.randomUUID()+ext;
            try(InputStream in=part.getInputStream()){Files.copy(in,folder.resolve(filename),StandardCopyOption.REPLACE_EXISTING);}
            paths.add("uploads/"+filename);
        }return paths;
    }
}
