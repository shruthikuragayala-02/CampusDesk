package com.campusdesk.controller;

import com.campusdesk.security.AuthRequestDiagnostics;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

public class AuthControllerDiagnosticInterceptor implements HandlerInterceptor {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthControllerDiagnosticInterceptor.class);

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception exception) {
        AuthRequestDiagnostics.log(LOGGER, request, response.getStatus());
    }
}
