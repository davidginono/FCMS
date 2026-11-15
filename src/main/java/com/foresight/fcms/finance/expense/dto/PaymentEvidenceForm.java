package com.foresight.fcms.finance.expense.dto;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.UUID;

public class PaymentEvidenceForm {
    @NotNull private LocalDate paidOn;

    public LocalDate getPaidOn() {
        return paidOn;
    }

    public void setPaidOn(LocalDate value) {
        paidOn = value;
    }

    @NotNull @Positive private Long payerId;

    public Long getPayerId() {
        return payerId;
    }

    public void setPayerId(Long value) {
        payerId = value;
    }

    @NotBlank
    @Size(max = 100)
    private String paymentReference = "";

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String value) {
        paymentReference = value;
    }

    @NotBlank
    @Size(max = 200)
    private String documentReference = "";

    public String getDocumentReference() {
        return documentReference;
    }

    public void setDocumentReference(String value) {
        documentReference = value;
    }

    @NotBlank
    @Size(max = 1000)
    private String explanation = "";

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String value) {
        explanation = value;
    }

    @NotNull
    @Size(max = 200)
    private String openingPolicyReference = "";

    public String getOpeningPolicyReference() {
        return openingPolicyReference;
    }

    public void setOpeningPolicyReference(String value) {
        openingPolicyReference = value;
    }

    @NotNull private UUID requestKey = UUID.randomUUID();

    public UUID getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(UUID value) {
        requestKey = value;
    }
}
