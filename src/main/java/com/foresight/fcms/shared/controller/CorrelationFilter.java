package com.foresight.fcms.shared.controller;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CorrelationFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
            throws ServletException, IOException {
        String supplied = r.getHeader("X-Request-ID");
        String id =
                supplied != null && supplied.matches("[a-zA-Z0-9-]{1,64}")
                        ? supplied
                        : java.util.UUID.randomUUID().toString();
        MDC.put("requestId", id);
        s.setHeader("X-Request-ID", id);
        try {
            chain.doFilter(r, s);
        } finally {
            MDC.remove("requestId");
        }
    }
}
