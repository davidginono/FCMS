package com.foresight.fcms.accounting.dto;

public class PeriodForm {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String name = "";

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    @jakarta.validation.constraints.NotNull private java.time.LocalDate startsOn;

    public java.time.LocalDate getStartsOn() {
        return startsOn;
    }

    public void setStartsOn(java.time.LocalDate value) {
        this.startsOn = value;
    }

    @jakarta.validation.constraints.NotNull private java.time.LocalDate endsOn;

    public java.time.LocalDate getEndsOn() {
        return endsOn;
    }

    public void setEndsOn(java.time.LocalDate value) {
        this.endsOn = value;
    }
}
