package com.foresight.fcms.auth.dto;

public class UserForm {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String firstName = "";

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String v) {
        firstName = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String lastName = "";

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String v) {
        lastName = v;
    }

    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Email
    @jakarta.validation.constraints.Size(max = 100)
    private String email = "";

    public String getEmail() {
        return email;
    }

    public void setEmail(String v) {
        email = v;
    }

    @jakarta.validation.constraints.NotNull private Long roleId;

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long v) {
        roleId = v;
    }

    @jakarta.validation.constraints.NotNull private Long statusId = 1L;

    public Long getStatusId() {
        return statusId;
    }

    public void setStatusId(Long v) {
        statusId = v;
    }

    @jakarta.validation.constraints.Size(max = 72)
    private String password = "";

    public String getPassword() {
        return password;
    }

    public void setPassword(String v) {
        password = v;
    }

    private long version = 0;

    public long getVersion() {
        return version;
    }

    public void setVersion(long v) {
        version = v;
    }

    @jakarta.validation.constraints.Size(max = 100)
    private java.util.List<String> claims = new java.util.ArrayList<>();

    public java.util.List<String> getClaims() {
        return claims;
    }

    public void setClaims(java.util.List<String> v) {
        claims = v;
    }
}
