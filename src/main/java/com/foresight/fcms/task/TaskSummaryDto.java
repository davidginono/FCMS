package com.foresight.fcms.task;

import java.time.LocalDate;

public record TaskSummaryDto(
    Integer id,
    String assignee,
    String clientName,
    String taskType,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    String activities
) {
    static TaskSummaryDto from(Task task) {
        return new TaskSummaryDto(
            task.getId(),
            task.getUser().getFullName(),
            task.getSaccos().getSaccosName(),
            task.getTaskType().getName(),
            task.getTaskStatus().getStatusName(),
            task.getStartDate(),
            task.getEndDate(),
            task.getActivities()
        );
    }
}
