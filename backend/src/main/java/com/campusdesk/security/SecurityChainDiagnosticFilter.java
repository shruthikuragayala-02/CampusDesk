package com.campusdesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class SecurityChainDiagnosticFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(SecurityChainDiagnosticFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } finally {
            AuthRequestDiagnostics.log(LOGGER, request, response.getStatus());
        }
    }
}
