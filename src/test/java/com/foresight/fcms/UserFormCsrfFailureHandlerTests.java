package com.foresight.fcms;

import com.foresight.fcms.auth.UserFormCsrfFailureHandler;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

import static org.junit.jupiter.api.Assertions.*;

class UserFormCsrfFailureHandlerTests {
    private final UserFormCsrfFailureHandler handler = new UserFormCsrfFailureHandler();

    @Test
    void rejectedCreateKeepsForbiddenStatusAndOffersOnlyAControlledRecoveryDestination() throws Exception {
        var request = new MockHttpServletRequest("POST", "/admin/users");
        request.setParameter("returnTo", "https://untrusted.invalid/");
        var response = new MockHttpServletResponse();
        handler.handle(request, response, new MissingCsrfTokenException("synthetic"));
        assertEquals(403, response.getStatus());
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertEquals("/WEB-INF/jsp/auth/user-form-session-error.jsp", response.getForwardedUrl());
        assertEquals("/admin/users/new", request.getAttribute("userFormRecoveryUrl"));
    }

    @Test
    void contextPathAndNumericEditIdStayInsideTheSameApplication() throws Exception {
        var request = new MockHttpServletRequest("POST", "/fcms/admin/users/7");
        request.setContextPath("/fcms");
        var response = new MockHttpServletResponse();
        handler.handle(request, response, new MissingCsrfTokenException("synthetic"));
        assertEquals(403, response.getStatus());
        assertEquals("/admin/users/7/edit", request.getAttribute("userFormRecoveryUrl"));
    }

    @Test
    void genuinePermissionDenialsAndOtherRoutesRetainTheirExistingForbiddenHandling() throws Exception {
        for (var request : new MockHttpServletRequest[]{new MockHttpServletRequest("POST", "/login"), new MockHttpServletRequest("POST", "/admin/users")}) {
            var response = new MockHttpServletResponse();
            handler.handle(request, response, request.getRequestURI().equals("/login")
                    ? new MissingCsrfTokenException("synthetic") : new AccessDeniedException("denied"));
            assertEquals(403, response.getStatus()); assertNull(response.getForwardedUrl());
            assertNull(request.getAttribute("userFormRecoveryUrl"));
        }
    }
}
