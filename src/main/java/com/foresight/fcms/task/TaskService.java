package com.foresight.fcms.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository tasks;

    public TaskService(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Transactional(readOnly = true)
    public Page<TaskSummaryDto> findTasks(Pageable pageable) {
        return tasks.findAll(pageable).map(TaskSummaryDto::from);
    }

    @Transactional(readOnly = true)
    public long countTasks() {
        return tasks.count();
    }
}
