package com.foresight.fcms.auth.controller;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.foresight.fcms.shared.controller.ResourceFeedback;
import com.foresight.fcms.auth.service.AccountService;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/account")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService a) {
        accounts = a;
    }

    @GetMapping
    public String index(Model m) {
        m.addAttribute("form", accounts.profile());
        m.addAttribute("hasPhoto", accounts.hasPhoto());
        return "account/profile";
    }

    @PostMapping
    public String profile(
            @Valid @ModelAttribute("form") AccountService.Profile f, BindingResult b, RedirectAttributes redirect) {
        if (b.hasErrors()) return "account/profile";
        accounts.profile(f);
        return ResourceFeedback.success(redirect, "/account", "feedback.saved", null);
    }

    @GetMapping("/password")
    public String password(Model m) {
        m.addAttribute("form", new AccountService.PasswordChange("", "", ""));
        return "account/password";
    }

    @PostMapping("/password")
    public String password(
            @Valid @ModelAttribute("form") AccountService.PasswordChange f,
            BindingResult b,
            jakarta.servlet.http.HttpServletRequest req) {
        if (b.hasErrors()) return "account/password";
        try {
            accounts.password(f);
        } catch (IllegalArgumentException e) {
            b.reject(
                    "error.passwordDefault".equals(e.getMessage())
                            ? "error.passwordDefault"
                            : "error.password");
            return "account/password";
        }
        req.getSession().invalidate();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        return "redirect:/login?passwordChanged";
    }

    @PostMapping("/photo")
    public String photo(
            @RequestParam org.springframework.web.multipart.MultipartFile photo, Model m, RedirectAttributes redirect) {
        try {
            accounts.photo(photo);
            return ResourceFeedback.success(redirect, "/account", "feedback.saved", null);
        } catch (IllegalArgumentException e) {
            m.addAttribute(
                    "photoError",
                    java.util.Set.of(
                                            "error.uploadBusy",
                                            "error.uploadRejected",
                                            "error.uploadUnavailable")
                                    .contains(e.getMessage())
                            ? e.getMessage()
                            : "error.invalid");
            m.addAttribute("form", accounts.profile());
            m.addAttribute("hasPhoto", accounts.hasPhoto());
            return "account/profile";
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
            m.addAttribute("photoError", "error.stale");
            m.addAttribute("form", accounts.profile());
            m.addAttribute("hasPhoto", accounts.hasPhoto());
            return "account/profile";
        }
    }

    @GetMapping("/photo")
    @ResponseBody
    public org.springframework.http.ResponseEntity<org.springframework.core.io.Resource> photo() {
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.IMAGE_PNG)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(accounts.photo());
    }
}
