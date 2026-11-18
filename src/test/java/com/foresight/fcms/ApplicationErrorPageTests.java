package com.foresight.fcms;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.shared.controller.ApplicationErrorViewResolver;

import jakarta.servlet.RequestDispatcher;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

class ApplicationErrorPageTests {
    private final ApplicationErrorViewResolver resolver = new ApplicationErrorViewResolver();

    @Test
    void logoutRecoveryUsesOnlyTheServletErrorPathAndKeepsSensitiveDetailsOutOfTheView() {
        var request = new MockHttpServletRequest("POST", "/fcms/error");
        request.setContextPath("/fcms");
        request.setUserPrincipal(() -> "fixture@test.invalid");
        request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/fcms/logout");
        request.setAttribute(RequestDispatcher.ERROR_METHOD, "POST");
        var view =
                resolver.resolveErrorView(
                        request,
                        HttpStatus.FORBIDDEN,
                        Map.of("trace", "private stack trace", "message", "private exception"));
        assertEquals("shared/error", view.getViewName());
        assertEquals(
                Map.of("errorCode", "error.logout", "errorStatus", 403, "logoutRejected", true),
                view.getModel());
    }

    @Test
    void otherErrorsHaveSafeMessagesAndNeverOfferToReplayAnAction() {
        for (var entry :
                Map.of(
                                400,
                                "error.invalid",
                                401,
                                "error.session",
                                403,
                                "error.denied",
                                404,
                                "error.notFound",
                                500,
                                "error.unavailable")
                        .entrySet()) {
            var request = new MockHttpServletRequest("POST", "/error");
            request.setParameter("path", "/logout");
            var view =
                    resolver.resolveErrorView(
                            request, HttpStatus.valueOf(entry.getKey()), Map.of());
            assertEquals(entry.getValue(), view.getModel().get("errorCode"));
            assertEquals(entry.getKey(), view.getModel().get("errorStatus"));
            assertEquals(false, view.getModel().get("logoutRejected"));
        }
    }
}
