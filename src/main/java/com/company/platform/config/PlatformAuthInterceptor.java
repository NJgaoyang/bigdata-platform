package com.company.platform.config;

import com.company.platform.system.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import com.company.platform.system.AuditService;

@Component
public class PlatformAuthInterceptor implements HandlerInterceptor {
    private final AuthService auth;
    private final AuditService audit;
    public PlatformAuthInterceptor(AuthService auth, AuditService audit) { this.auth = auth; this.audit = audit; }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path.equals("/api/health") || path.startsWith("/api/auth/") || request.getMethod().equalsIgnoreCase("OPTIONS")) return true;
        if (!auth.enabled()) {
            request.setAttribute("platform.operator", "admin");
            auditMutation(request, path, "admin");
            return true;
        }
        String header = request.getHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
        if (auth.authenticate(token)) {
            if (!auth.hasPermission(token, request.getMethod(), path)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"当前用户没有该模块权限\"}");
                audit.record("ACCESS_DENIED", "HTTP", null, request.getMethod() + " " + path, auth.currentUsername(token));
                return false;
            }
            request.setAttribute("platform.operator", auth.currentUsername(token));
            auditMutation(request, path, auth.currentUsername(token));
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"data\":null,\"message\":\"请先登录平台\"}");
        return false;
    }

    private void auditMutation(HttpServletRequest request, String path, String operator) {
        if (!request.getMethod().equalsIgnoreCase("GET")) {
            audit.record("API_MUTATION", "HTTP", null, request.getMethod() + " " + path, operator);
        }
    }
}
