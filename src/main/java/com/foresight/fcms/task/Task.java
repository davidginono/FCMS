package com.foresight.fcms.task;

import com.foresight.fcms.auth.UserAccount;
import com.foresight.fcms.client.Saccos;
import com.foresight.fcms.reference.TaskStatus;
import com.foresight.fcms.reference.TaskType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "saccos_id")
    private Saccos saccos;

    @ManyToOne(optional = false)
    @JoinColumn(name = "task_type_id")
    private TaskType taskType;

    @ManyToOne(optional = false)
    @JoinColumn(name = "task_status_id")
    private TaskStatus taskStatus;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    private String activities;

    @Column(nullable = false)
    private BigDecimal allowance;

    @Column(nullable = false)
    private BigDecimal amount;

    private String observation;

    private String remarks;

    @Column(name = "created_date", nullable = false)
    private LocalDateTime createdDate;

    public Integer getId() {
        return id;
    }

    public UserAccount getUser() {
        return user;
    }

    public Saccos getSaccos() {
        return saccos;
    }

    public TaskType getTaskType() {
        return taskType;
    }

    public TaskStatus getTaskStatus() {
        return taskStatus;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getActivities() {
        return activities;
    }

    public BigDecimal getAllowance() {
        return allowance;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getObservation() {
        return observation;
    }

    public String getRemarks() {
        return remarks;
    }

    public LocalDateTime getCreatedDate() {
        return createdDate;
    }
}
