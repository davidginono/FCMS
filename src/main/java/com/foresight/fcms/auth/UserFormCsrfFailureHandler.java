package com.foresight.fcms.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.access.AccessDeniedHandlerImpl;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;

/** Rejected user-form POSTs remain rejected; never echo or replay their submitted fields. */
public final class UserFormCsrfFailureHandler implements AccessDeniedHandler {
    private final AccessDeniedHandler fallback = new AccessDeniedHandlerImpl();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception)
            throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (exception instanceof CsrfException
                && "POST".equals(request.getMethod())
                && "/logout".equals(path)) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null
                    || !authentication.isAuthenticated()
                    || authentication instanceof AnonymousAuthenticationToken) {
                response.setHeader("Cache-Control", "no-store");
                response.sendRedirect(request.getContextPath() + "/login?logout");
                return;
            }
        }
        if (exception instanceof CsrfException
                && "POST".equals(request.getMethod())
                && path.matches("/admin/users(?:/[0-9]+)?")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setHeader("Cache-Control", "no-store");
            request.setAttribute(
                    "userFormRecoveryUrl",
                    path.equals("/admin/users") ? "/admin/users/new" : path + "/edit");
            request.getRequestDispatcher("/WEB-INF/jsp/auth/user-form-session-error.jsp")
                    .forward(request, response);
            return;
        }
        fallback.handle(request, response, exception);
    }
}
