package com.foresight.fcms.task;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public String index(Model model) {
        var page = taskService.findTasks(PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "id")));
        model.addAttribute("tasks", page.getContent());
        model.addAttribute("totalTasks", page.getTotalElements());
        return "tasks/index";
    }
}
