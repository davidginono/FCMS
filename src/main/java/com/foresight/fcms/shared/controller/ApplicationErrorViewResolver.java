package com.foresight.fcms.shared.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/** Safe HTML recovery for servlet errors; Boot retains its JSON error handling. */
@Component
public class ApplicationErrorViewResolver implements ErrorViewResolver {
    @Override
    public ModelAndView resolveErrorView(
            HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        boolean logoutRejected =
                status == HttpStatus.FORBIDDEN
                        && "POST".equals(request.getAttribute(RequestDispatcher.ERROR_METHOD))
                        && (request.getContextPath() + "/logout")
                                .equals(request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI));
        String code =
                logoutRejected
                        ? (request.getUserPrincipal() == null ? "error.session" : "error.logout")
                        : switch (status.value()) {
                            case 400 -> "error.invalid";
                            case 401 -> "error.session";
                            case 403 -> "error.denied";
                            case 404 -> "error.notFound";
                            default -> "error.unavailable";
                        };
        var view = new ModelAndView("shared/error");
        view.addObject("errorCode", code);
        view.addObject("errorStatus", status.value());
        view.addObject("logoutRejected", logoutRejected);
        return view;
    }
}
