package com.foresight.fcms.finance;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/finance")
public class FinanceController {

    private final FinanceService financeService;

    public FinanceController(FinanceService financeService) {
        this.financeService = financeService;
    }

    @GetMapping("/expenses")
    public String expenses(Model model) {
        var page = financeService.findExpenses(PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("expenses", page.getContent());
        model.addAttribute("totalExpenses", page.getTotalElements());
        return "finance/expenses";
    }
}
