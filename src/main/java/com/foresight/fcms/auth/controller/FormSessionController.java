package com.foresight.fcms.auth.controller;

import com.foresight.fcms.auth.dto.UserFormSession;
import com.foresight.fcms.auth.service.AccessService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class FormSessionController {
    private final AccessService access;

    public FormSessionController(AccessService access) { this.access = access; }

    @GetMapping("/form-session")
    @ResponseBody
    public ResponseEntity<UserFormSession> current(CsrfToken token) {
        var actor = access.current();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new UserFormSession(Long.toString(actor.id()), token.getParameterName(), token.getToken()));
    }
}
