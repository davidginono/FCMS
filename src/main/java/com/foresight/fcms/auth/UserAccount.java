package com.foresight.fcms.auth;

import com.foresight.fcms.reference.AccountStatus;
import com.foresight.fcms.reference.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "leave_email", length = 100)
    private String leaveEmail;

    @Column(name = "image_path")
    private String imagePath;

    @Column(nullable = false)
    private String password;

    @ManyToOne(optional = false)
    @JoinColumn(name = "status_id")
    private AccountStatus status;

    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id")
    private Role role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "token_expiry")
    private Instant tokenExpiry;

    private String otp;

    @Column(name = "otp_expiry")
    private Instant otpExpiry;

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return (firstName + " " + lastName).trim();
    }

    public String getEmail() {
        return email;
    }

    public String getLeaveEmail() {
        return leaveEmail;
    }

    public String getImagePath() {
        return imagePath;
    }

    public String getPassword() {
        return password;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Role getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getResetToken() {
        return resetToken;
    }

    public Instant getTokenExpiry() {
        return tokenExpiry;
    }

    public String getOtp() {
        return otp;
    }

    public Instant getOtpExpiry() {
        return otpExpiry;
    }
}
