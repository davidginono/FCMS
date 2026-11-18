package com.foresight.fcms.shared.controller;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.Serializable;
import java.util.UUID;

/** Feedback is issued only after the command returns, and belongs to its destination GET. */
public final class ResourceFeedback {
    private ResourceFeedback() {}

    public record Notice(String type, String messageKey, String resourceUrl) implements Serializable {}

    public static String success(RedirectAttributes redirect, String destination, String key, String resourceUrl) {
        return send(redirect, destination, new Notice("success", key, resourceUrl));
    }

    public static String info(RedirectAttributes redirect, String destination, String key) {
        return send(redirect, destination, new Notice("info", key, null));
    }

    public static String failure(RedirectAttributes redirect, String destination, String key) {
        return send(redirect, destination, new Notice("error", key, null));
    }

    private static String send(RedirectAttributes redirect, String destination, Notice notice) {
        redirect.addFlashAttribute("resourceNotice", notice);
        // Match Spring's FlashMap to this GET, so a parallel table refresh cannot consume it.
        return "redirect:" + destination + (destination.contains("?") ? "&" : "?") + "_notice=" + UUID.randomUUID();
    }
}
