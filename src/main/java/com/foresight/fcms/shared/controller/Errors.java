package com.foresight.fcms.shared.controller;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class Errors {
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public String validation(HttpServletResponse res, Model m) {
        res.setStatus(400);
        m.addAttribute("errorCode", "error.invalid");
        return "shared/error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String invalid(HttpServletResponse res, Model m) {
        res.setStatus(400);
        m.addAttribute("errorCode", "error.invalid");
        return "shared/error";
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public String conflict(HttpServletResponse res, Model m) {
        res.setStatus(409);
        m.addAttribute("errorCode", "error.conflict");
        return "shared/error";
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public String stale(HttpServletResponse res, Model m) {
        res.setStatus(409);
        m.addAttribute("errorCode", "error.stale");
        return "shared/error";
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String denied(HttpServletResponse res, Model m) {
        res.setStatus(403);
        m.addAttribute("errorCode", "error.denied");
        return "shared/error";
    }
}
