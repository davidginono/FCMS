package com.foresight.fcms.shared.controller;

import com.foresight.fcms.auth.service.UserDirectoryService;
import com.foresight.fcms.client.service.ClientDirectoryService;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lookups")
public class LookupController {
    private final UserDirectoryService users;
    private final ClientDirectoryService clients;

    public LookupController(UserDirectoryService users, ClientDirectoryService clients) {
        this.users = users;
        this.clients = clients;
    }

    @GetMapping("/staff")
    public com.foresight.fcms.shared.dto.ChoicePage<UserDirectoryService.Choice> staff(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page) {
        return users.search(search, page);
    }

    @GetMapping("/clients")
    public com.foresight.fcms.shared.dto.ChoicePage<ClientDirectoryService.Choice> clients(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page) {
        return clients.search(search, page);
    }
}
