package com.handcraftshop.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiUtil {
    public static final Gson GSON = new Gson();
    private ApiUtil() {}

    public static JsonObject readJson(HttpServletRequest request) throws IOException {
        String raw = request.getReader().lines().reduce("", (a, b) -> a + b);
        if (raw.isBlank()) return new JsonObject();
        return GSON.fromJson(raw, JsonObject.class);
    }

    public static void send(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(GSON.toJson(body));
    }

    public static void ok(HttpServletResponse response, Object body) throws IOException {
        send(response, 200, body);
    }

    public static void error(HttpServletResponse response, int status, String message) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", false);
        result.put("error", message == null ? "Request failed" : message);
        send(response, status, result);
    }

    public static String string(JsonObject json, String key, String fallback) {
        return json != null && json.has(key) && !json.get(key).isJsonNull()
                ? json.get(key).getAsString().trim() : fallback;
    }

    public static int integer(JsonObject json, String key, int fallback) {
        try { return json != null && json.has(key) ? json.get(key).getAsInt() : fallback; }
        catch (Exception e) { return fallback; }
    }

    public static Integer sessionId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object value = session.getAttribute("userId");
        return value instanceof Integer ? (Integer) value : null;
    }

    public static String role(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session == null ? null : (String) session.getAttribute("role");
    }

    public static boolean requireRole(HttpServletRequest request, HttpServletResponse response, String role) throws IOException {
        if (!role.equals(role(request))) {
            error(response, 401, "Please sign in with the required account to continue.");
            return false;
        }
        return true;
    }
}
