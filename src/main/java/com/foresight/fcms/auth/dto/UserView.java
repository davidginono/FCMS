package com.foresight.fcms.auth.dto;

public record UserView(
        long id,
        String firstName,
        String lastName,
        String email,
        long roleId,
        String roleName,
        long statusId,
        String status,
        long version) {
    public String name() {
        return firstName + " " + lastName;
    }
}
