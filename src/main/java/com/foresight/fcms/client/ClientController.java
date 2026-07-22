package com.foresight.fcms.client;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping("/saccos")
    public String saccos(Model model) {
        var page = clientService.findSaccos(PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("clients", page.getContent());
        model.addAttribute("totalClients", page.getTotalElements());
        return "clients/saccos";
    }
}
