package com.foresight.fcms.client.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Read-only redirects for canonical PHP client entry points; mutations use native CSRF forms. */
@Controller
public class LegacyClientActionsController {
    @GetMapping({"/paidamcos.php", "/unpaidamcos.php"})
    public String payment(
            HttpServletRequest request,
            @RequestParam(name = "fyear_id", required = false) Long year,
            RedirectAttributes redirect) {
        if (year != null) redirect.addAttribute("financialYearId", year);
        redirect.addAttribute(
                "paymentStatusId", request.getServletPath().equals("/paidamcos.php") ? 1 : 2);
        return "redirect:/clients/amcos";
    }

    @PostMapping({"/print_amcos.php", "/print_amcos_excel.php"})
    public String export(
            HttpServletRequest request,
            @RequestParam(name = "fyear_id", required = false) Long year,
            RedirectAttributes redirect) {
        if (year != null) redirect.addAttribute("financialYearId", year);
        return "redirect:/clients/amcos/"
                + (request.getServletPath().endsWith("_excel.php") ? "export.csv" : "print.pdf");
    }

    @GetMapping({
        "/amcos.php",
        "/user_amcos.php",
        "/add_amcos.php",
        "/update_amcos.php",
        "/view_amcos.php"
    })
    public String amcos(
            HttpServletRequest request,
            @RequestParam(required = false) Long id,
            @RequestParam(name = "fyear_id", required = false) Long year,
            RedirectAttributes redirect) {
        if (year != null) redirect.addAttribute("financialYearId", year);
        String path = request.getServletPath();
        String suffix =
                path.equals("/add_amcos.php")
                        ? "/new"
                        : id == null
                                ? ""
                                : path.equals("/update_amcos.php")
                                        ? "/" + id + "/edit"
                                        : path.equals("/view_amcos.php") ? "/" + id : "";
        return "redirect:/clients/amcos" + suffix;
    }

    @GetMapping({
        "/amcos_update_contract.php",
        "/union_update_contract.php",
        "/micro_update_contract.php",
        "/others_update_contract.php"
    })
    public String contract(HttpServletRequest request, @RequestParam(required = false) Long id) {
        String slug =
                Map.of(
                                "/amcos_update_contract.php",
                                "amcos",
                                "/union_update_contract.php",
                                "unions",
                                "/micro_update_contract.php",
                                "microfinance",
                                "/others_update_contract.php",
                                "others")
                        .get(request.getServletPath());
        return "redirect:/clients/" + slug + (id == null ? "" : "/" + id + "/contract/edit");
    }

    @GetMapping({
        "/delete_dialog.php",
        "/delete_dialog2.php",
        "/delete_dialog3.php",
        "/delete_saccos.php",
        "/delete_amcos.php",
        "/delete_union.php",
        "/delete_microfinance.php",
        "/others_delete_dialog.php",
        "/delete_others.php"
    })
    public String lifecycle(HttpServletRequest request, @RequestParam(required = false) Long id) {
        String slug =
                switch (request.getServletPath()) {
                    case "/delete_dialog.php", "/delete_saccos.php" -> "saccos";
                    case "/delete_dialog2.php", "/delete_amcos.php" -> "amcos";
                    case "/delete_dialog3.php", "/delete_union.php" -> "unions";
                    case "/delete_microfinance.php" -> "microfinance";
                    default -> "others";
                };
        return "redirect:/clients/" + slug + (id == null ? "" : "/" + id + "/lifecycle");
    }
}
