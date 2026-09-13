package com.company.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final PlatformAuthInterceptor authInterceptor;
    public WebConfig(PlatformAuthInterceptor authInterceptor) { this.authInterceptor = authInterceptor; }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*", "http://192.168.*:*", "http://10.*:*", "http://172.*:*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    @Override
    public void addViewControllers(org.springframework.web.servlet.config.annotation.ViewControllerRegistry registry) {
        String[] routes = {
                "/", "/login", "/forbidden",
                "/overview", "/sources", "/integration", "/development", "/explore", "/lineage", "/workflow", "/operations", "/assets", "/settings",
                "/integration/overview", "/integration/datasources", "/integration/batch", "/integration/realtime", "/integration/instances",
                "/development/workspace", "/development/versions",
                "/workflow/definitions",
                "/operations/overview", "/operations/instances", "/operations/failures", "/operations/alerts",
                "/metadata/catalog",
                "/metrics/overview", "/metrics/manage", "/metrics/dimensions", "/metrics/lineage",
                "/assets/catalog", "/assets/favorites",
                "/release/history", "/release/queue", "/release/policy",
                "/system/users", "/system/roles", "/system/data-source-permissions", "/system/environments", "/system/audit"
        };
        for (String route : routes) registry.addViewController(route).setViewName("forward:/index.html");
    }
}
