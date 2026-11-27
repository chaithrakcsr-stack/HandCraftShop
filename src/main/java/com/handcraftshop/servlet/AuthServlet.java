package com.handcraftshop.servlet;

import com.handcraftshop.config.DBConnection;
import com.handcraftshop.service.EmailService;
import com.handcraftshop.util.ApiUtil;
import com.handcraftshop.util.PasswordUtil;
import com.google.gson.JsonObject;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@WebServlet(urlPatterns = "/api/auth/*", loadOnStartup = 1)
public class AuthServlet extends HttpServlet {
    @Override
    public void init() throws ServletException {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement check = con.prepareStatement("SELECT admin_id FROM admins WHERE username=?")) {
            String username = System.getenv().getOrDefault("HANDCRAFT_ADMIN_USER", "admin@handcraft.local");
            check.setString(1, username);
            try (ResultSet rs = check.executeQuery()) {
                if (!rs.next()) {
                    try (PreparedStatement insert = con.prepareStatement("INSERT INTO admins(username,password_hash,active) VALUES(?,?,1)")) {
                        insert.setString(1, username);
                        insert.setString(2, PasswordUtil.hash(System.getenv().getOrDefault("HANDCRAFT_ADMIN_PASSWORD", "Admin@123")));
                        insert.executeUpdate();
                    }
                }
            }
        } catch (SQLException e) {
            throw new ServletException("Could not initialize the admin account. Run database/handcraft_shop_upgrade.sql first.", e);
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if ("/me".equals(req.getPathInfo())) {
            HttpSession s = req.getSession(false);
            if (s == null || s.getAttribute("role") == null) {
                ApiUtil.send(resp, 200, Map.of("authenticated", false));
                return;
            }
            Map<String,Object> user = new LinkedHashMap<>();
            user.put("authenticated", true);
            user.put("role", s.getAttribute("role"));
            user.put("id", s.getAttribute("userId"));
            user.put("name", s.getAttribute("displayName"));
            user.put("email", s.getAttribute("email"));
            ApiUtil.ok(resp, user);
            return;
        }
        ApiUtil.error(resp, 404, "Unknown authentication endpoint.");
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String path = req.getPathInfo();
        JsonObject body = ApiUtil.readJson(req);
        try {
            if ("/register".equals(path)) register(body, resp);
            else if ("/login".equals(path)) login(body, req, resp);
            else if ("/forgot-password".equals(path)) forgotPassword(body, resp);
            else if ("/reset-password".equals(path)) resetPassword(body, resp);
            else if ("/logout".equals(path)) {
                HttpSession s = req.getSession(false);
                if (s != null) s.invalidate();
                ApiUtil.ok(resp, Map.of("success", true, "message", "Signed out successfully."));
            } else ApiUtil.error(resp, 404, "Unknown authentication endpoint.");
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) ApiUtil.error(resp, 409, "An account with this email already exists.");
            else ApiUtil.error(resp, 500, "Database error: " + e.getMessage());
        }
    }


    private void forgotPassword(JsonObject b, HttpServletResponse resp) throws SQLException, IOException {
        String type=ApiUtil.string(b,"accountType","customer").toLowerCase();
        String email=ApiUtil.string(b,"email","").toLowerCase();
        String table="customer".equals(type)?"customers":("seller".equals(type)?"sellers":("admin".equals(type)?"admins":""));
        if(table.isEmpty()||email.isBlank()){ApiUtil.error(resp,400,"Choose a valid account type and email.");return;}
        String column="admin".equals(type)?"username":"email";
        try(Connection c=DBConnection.getConnection();PreparedStatement check=c.prepareStatement("SELECT 1 FROM "+table+" WHERE "+column+"=?")){
            check.setString(1,email);try(ResultSet r=check.executeQuery()){
                if(r.next()){
                    String token=UUID.randomUUID().toString().replace("-","");
                    try(PreparedStatement p=c.prepareStatement("INSERT INTO password_reset_tokens(account_type,email,token_hash,expires_at) VALUES(?,?,?,DATE_ADD(NOW(),INTERVAL 20 MINUTE))")){
                        p.setString(1,type);p.setString(2,email);p.setString(3,PasswordUtil.hash(token));p.executeUpdate();
                    }
                    String base=System.getenv().getOrDefault("HANDCRAFT_PUBLIC_URL","http://localhost:8080/HandcraftShop");
                    String link=base+"/reset-password.html?token="+token+"&type="+type+"&email="+java.net.URLEncoder.encode(email,java.nio.charset.StandardCharsets.UTF_8);
                    EmailService.send(email,"Handcraft Shop password reset","Use this one-time link within 20 minutes to set a new password: "+link);
                    System.out.println("[Local development password reset link] "+link);
                }
            }
        }
        ApiUtil.ok(resp,Map.of("success",true,"message","If an account exists for that email, a password reset link has been sent. In local mode, check the Eclipse/Tomcat console if SMTP is not configured."));
    }

    private void resetPassword(JsonObject b,HttpServletResponse resp)throws SQLException,IOException{
        String type=ApiUtil.string(b,"accountType","customer").toLowerCase();String email=ApiUtil.string(b,"email","").toLowerCase();
        String token=ApiUtil.string(b,"token","");String password=ApiUtil.string(b,"password","");
        if(password.length()<8||token.isBlank()){ApiUtil.error(resp,400,"A valid reset token and a password of at least 8 characters are required.");return;}
        try(Connection c=DBConnection.getConnection()){
            c.setAutoCommit(false);
            try(PreparedStatement p=c.prepareStatement("SELECT reset_id,token_hash FROM password_reset_tokens WHERE account_type=? AND email=? AND used_at IS NULL AND expires_at>NOW() ORDER BY reset_id DESC LIMIT 10 FOR UPDATE")){
                p.setString(1,type);p.setString(2,email);long resetId=0;try(ResultSet r=p.executeQuery()){while(r.next()){if(PasswordUtil.verify(token,r.getString("token_hash"))){resetId=r.getLong("reset_id");break;}}}
                if(resetId==0){c.rollback();ApiUtil.error(resp,400,"This reset link is invalid or has expired.");return;}
                String table="customer".equals(type)?"customers":("seller".equals(type)?"sellers":("admin".equals(type)?"admins":""));
                String col="admin".equals(type)?"username":("customer".equals(type)||"seller".equals(type)?"email":"");
                String passCol="admin".equals(type)?"password_hash":"password";
                if(table.isEmpty()){c.rollback();ApiUtil.error(resp,400,"Invalid account type.");return;}
                try(PreparedStatement u=c.prepareStatement("UPDATE "+table+" SET "+passCol+"=? WHERE "+col+"=?")){u.setString(1,PasswordUtil.hash(password));u.setString(2,email);if(u.executeUpdate()==0){c.rollback();ApiUtil.error(resp,404,"Account was not found.");return;}}
                try(PreparedStatement u=c.prepareStatement("UPDATE password_reset_tokens SET used_at=NOW() WHERE reset_id=?")){u.setLong(1,resetId);u.executeUpdate();}
                c.commit();ApiUtil.ok(resp,Map.of("success",true,"message","Password updated. You can now sign in."));
            }catch(SQLException e){c.rollback();throw e;}finally{c.setAutoCommit(true);}
        }
    }

    private void register(JsonObject b, HttpServletResponse resp) throws SQLException, IOException {
        String type = ApiUtil.string(b, "accountType", "customer").toLowerCase();
        String email = ApiUtil.string(b, "email", "").toLowerCase();
        String password = ApiUtil.string(b, "password", "");
        String phone = ApiUtil.string(b, "phone", "");
        if (email.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || password.length() < 8) {
            ApiUtil.error(resp, 400, "Enter a valid email and a password with at least 8 characters."); return;
        }
        String hash = PasswordUtil.hash(password);
        try (Connection con = DBConnection.getConnection()) {
            if ("customer".equals(type)) {
                String name = ApiUtil.string(b, "fullName", "");
                if (name.isBlank()) { ApiUtil.error(resp, 400, "Full name is required."); return; }
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO customers(full_name,email,phone,password) VALUES(?,?,?,?)")) {
                    ps.setString(1, name); ps.setString(2, email); ps.setString(3, phone); ps.setString(4, hash); ps.executeUpdate();
                }
                EmailService.send(email, "Welcome to Handcraft Shop", "Your customer account has been created successfully.");
                ApiUtil.send(resp, 201, Map.of("success", true, "message", "Customer account created. Please sign in."));
            } else if ("seller".equals(type)) {
                String shop = ApiUtil.string(b, "shopName", "");
                String owner = ApiUtil.string(b, "ownerName", "");
                String location = ApiUtil.string(b, "sellerLocation", "");
                if (shop.isBlank() || owner.isBlank() || location.isBlank()) {
                    ApiUtil.error(resp, 400, "Shop name, owner name and seller location are required."); return;
                }
                try (PreparedStatement ps = con.prepareStatement("INSERT INTO sellers(shop_name,owner_name,email,phone,password,seller_location,approval_status,shop_description,craft_speciality) VALUES(?,?,?,?,?,?,'PENDING',?,?)")) {
                    ps.setString(1, shop); ps.setString(2, owner); ps.setString(3, email); ps.setString(4, phone); ps.setString(5, hash);
                    ps.setString(6, location); ps.setString(7, ApiUtil.string(b, "shopDescription", ""));
                    ps.setString(8, ApiUtil.string(b, "craftSpeciality", "Handmade crafts")); ps.executeUpdate();
                }
                EmailService.send(email, "Handcraft Shop seller application received", "Your seller application is pending admin approval. We will notify you after review.");
                ApiUtil.send(resp, 201, Map.of("success", true, "message", "Seller application submitted. You can sign in to check its approval status."));
            } else ApiUtil.error(resp, 400, "Account type must be customer or seller.");
        }
    }

    private void login(JsonObject b, HttpServletRequest req, HttpServletResponse resp) throws SQLException, IOException {
        String type = ApiUtil.string(b, "accountType", "customer").toLowerCase();
        String email = ApiUtil.string(b, "email", "").toLowerCase();
        String password = ApiUtil.string(b, "password", "");
        if (email.isBlank() || password.isBlank()) { ApiUtil.error(resp, 400, "Email and password are required."); return; }
        String sql;
        String role = type;
        String idColumn;
        String nameColumn;
        String table;
        if ("admin".equals(type)) {
            table = "admins"; idColumn = "admin_id"; nameColumn = "username";
            sql = "SELECT admin_id AS account_id,username AS display_name,password_hash AS pass_value,active FROM admins WHERE username=?";
        } else if ("seller".equals(type)) {
            table = "sellers"; idColumn = "seller_id"; nameColumn = "shop_name";
            sql = "SELECT seller_id AS account_id,shop_name AS display_name,email,password AS pass_value,approval_status FROM sellers WHERE email=?";
        } else if ("customer".equals(type)) {
            table = "customers"; idColumn = "customer_id"; nameColumn = "full_name";
            sql = "SELECT customer_id AS account_id,full_name AS display_name,email,password AS pass_value,active FROM customers WHERE email=?";
        } else { ApiUtil.error(resp, 400, "Choose customer, seller or admin sign in."); return; }

        try (Connection con = DBConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !PasswordUtil.verify(password, rs.getString("pass_value"))) {
                    ApiUtil.error(resp, 401, "Email or password is incorrect."); return;
                }
                if (("admin".equals(type) || "customer".equals(type)) && !rs.getBoolean("active")) { ApiUtil.error(resp, 403, "This account is disabled. Contact the marketplace administrator."); return; }
                if ("seller".equals(type) && "REJECTED".equalsIgnoreCase(rs.getString("approval_status"))) {
                    ApiUtil.error(resp, 403, "Your seller application was rejected. Contact the marketplace admin."); return;
                }
                HttpSession old = req.getSession(false); if (old != null) old.invalidate();
                HttpSession session = req.getSession(true);
                session.setAttribute("role", role);
                session.setAttribute("userId", rs.getInt("account_id"));
                session.setAttribute("displayName", rs.getString("display_name"));
                session.setAttribute("email", "admin".equals(type) ? rs.getString("display_name") : rs.getString("email"));
                Map<String,Object> result = new LinkedHashMap<>();
                result.put("success", true); result.put("role", role); result.put("id", rs.getInt("account_id"));
                result.put("name", rs.getString("display_name"));
                if ("seller".equals(type)) result.put("approvalStatus", rs.getString("approval_status"));
                ApiUtil.ok(resp, result);
            }
        }
    }
}
