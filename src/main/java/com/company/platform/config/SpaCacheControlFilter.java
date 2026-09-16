package com.company.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SpaCacheControlFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (isSpaDocument(uri)) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        }
        filterChain.doFilter(request, response);
    }

    private boolean isSpaDocument(String uri) {
        if (uri == null || uri.isBlank()) return true;
        if (uri.startsWith("/api/")) return false;
        if ("/index.html".equals(uri)) return true;
        int slash = uri.lastIndexOf('/');
        String tail = slash >= 0 ? uri.substring(slash + 1) : uri;
        return !tail.contains(".");
    }
}
