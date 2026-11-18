package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.util.regex.Pattern;

/** Follow the real PRG destination and inspect the server-issued notice, not the query string. */
final class CreationFeedback {
    private CreationFeedback() {}

    static String resourceUrl(String html) {
        assertTrue(html.contains("data-resource-notice"), "The destination must display a notice");
        assertTrue(html.contains("data-message-type=\"success\""));
        assertTrue(html.contains("data-dismiss-notice"));
        var link = Pattern.compile("console-message__content[\\s\\S]*?<a href=\"([^\"]+)\"").matcher(html);
        assertTrue(link.find(), "The notice must link to the newly created resource");
        return link.group(1).replace("&amp;", "&");
    }

    static String follow(HttpClient web, HttpResponse<String> command) throws Exception {
        assertEquals(302, command.statusCode());
        URI destination = command.uri().resolve(command.headers().firstValue("Location").orElseThrow());
        assertTrue(destination.getQuery().contains("_notice="));
        var page = web.send(HttpRequest.newBuilder(destination).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().indexOf("data-resource-notice") < page.body().indexOf("erp-table"));
        return resourceUrl(page.body());
    }
}
