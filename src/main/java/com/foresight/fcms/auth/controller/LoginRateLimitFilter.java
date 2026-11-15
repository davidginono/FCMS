package com.foresight.fcms.auth.controller;

import com.foresight.fcms.auth.service.AuthRateLimitService;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimitService limits;

    public LoginRateLimitFilter(AuthRateLimitService l) {
        limits = l;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest r, HttpServletResponse s, FilterChain chain)
            throws ServletException, IOException {
        if (r.getMethod().equals("POST")
                && (r.getServletPath().equals("/login")
                        || r.getServletPath().startsWith("/password/"))
                && !limits.allow(r.getServletPath() + ":" + r.getRemoteAddr(), 20)) {
            s.sendError(429);
            return;
        }
        chain.doFilter(r, s);
    }
}
