package com.foresight.fcms.reference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Locale;

@Entity
@Table(name = "account_status")
public class AccountStatus {

    @Id private Long id;

    @Column(name = "status_name", nullable = false, unique = true)
    private String statusName;

    public Long getId() {
        return id;
    }

    public String getStatusName() {
        return statusName;
    }

    public boolean isActive() {
        return "active".equals(statusName == null ? "" : statusName.toLowerCase(Locale.ROOT));
    }
}
