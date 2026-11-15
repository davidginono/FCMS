package com.foresight.fcms.auth.controller;

import com.foresight.fcms.auth.service.PasswordRecoveryService;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/password")
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;

    public PasswordRecoveryController(PasswordRecoveryService r) {
        recovery = r;
    }

    @GetMapping("/forgot")
    public String forgot(Model m) {
        m.addAttribute("form", new Request(""));
        return "auth/forgot";
    }

    @PostMapping("/forgot")
    public String request(
            @Valid @ModelAttribute("form") Request f, BindingResult b, java.util.Locale locale) {
        if (b.hasErrors()) return "auth/forgot";
        recovery.request(f.email(), locale);
        return "redirect:/password/reset?requested";
    }

    @GetMapping("/reset")
    public String reset(Model m) {
        m.addAttribute("form", new Reset("", "", "", ""));
        return "auth/reset";
    }

    @PostMapping("/reset")
    public String reset(@Valid @ModelAttribute("form") Reset f, BindingResult b) {
        if (b.hasErrors()) return "auth/reset";
        try {
            if (!f.password().equals(f.confirmPassword())
                    || !recovery.reset(f.token(), f.otp(), f.password())) {
                b.reject("error.invalid");
                return "auth/reset";
            }
        } catch (IllegalArgumentException e) {
            b.reject(
                    "error.passwordDefault".equals(e.getMessage())
                            ? "error.passwordDefault"
                            : "error.password");
            return "auth/reset";
        }
        return "redirect:/login?passwordChanged";
    }

    public record Request(
            @jakarta.validation.constraints.Email
                    @jakarta.validation.constraints.NotBlank
                    @jakarta.validation.constraints.Size(max = 100)
                    String email) {}

    public record Reset(
            @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 100)
                    String token,
            @jakarta.validation.constraints.NotBlank
                    @jakarta.validation.constraints.Pattern(regexp = "[0-9]{6}")
                    String otp,
            @jakarta.validation.constraints.NotBlank
                    @jakarta.validation.constraints.Size(min = 12, max = 72)
                    String password,
            @jakarta.validation.constraints.NotBlank String confirmPassword) {}
}
