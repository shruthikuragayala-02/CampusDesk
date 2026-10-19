package com.campusdesk.security;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class AuthRequestDiagnostics {
    private AuthRequestDiagnostics() {
    }

    public static void log(Logger logger, HttpServletRequest request, int status) {
        String path = normalizedPath(request);
        if (path == null) {
            return;
        }

        String method = switch (request.getMethod()) {
            case "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS" -> request.getMethod();
            default -> "OTHER";
        };
        logger.info("method={} path={} status={} authorization_present={}",
            method, path, status, request.getHeader("Authorization") != null);
    }

    public static void logCurrent(Logger logger, int status) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            log(logger, servletAttributes.getRequest(), status);
        }
    }

    public static String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return switch (path) {
            case "/api/auth/register", "/api/auth/login",
                "/api/tickets", "/api/tickets/my",
                "/api/categories", "/api/locations", "/api/sla-policies" -> path;
            default -> {
                if (path.startsWith("/api/tickets/")) {
                    yield "/api/tickets/{route}";
                }
                if (path.startsWith("/api/admin/")) {
                    yield "/api/admin/{route}";
                }
                if (path.startsWith("/api/staff/")) {
                    yield "/api/staff/{route}";
                }
                yield null;
            }
        };
    }
}
