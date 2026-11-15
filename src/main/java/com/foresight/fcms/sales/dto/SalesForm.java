package com.foresight.fcms.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.*;

public class SalesForm {
    @NotBlank
    @Size(max = 20)
    private String clientType = "SACCOS";

    public String getClientType() {
        return clientType;
    }

    public void setClientType(String value) {
        clientType = value;
    }

    @NotBlank
    @Size(max = 150)
    private String clientName = "";

    public String getClientName() {
        return clientName;
    }

    public void setClientName(String value) {
        clientName = value;
    }

    @NotNull
    @Size(max = 100)
    private String region = "";

    public String getRegion() {
        return region;
    }

    public void setRegion(String value) {
        region = value;
    }

    @NotNull
    @Size(max = 150)
    private String contactName = "";

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String value) {
        contactName = value;
    }

    @NotNull
    @Size(max = 40)
    private String phone = "";

    public String getPhone() {
        return phone;
    }

    public void setPhone(String value) {
        phone = value;
    }

    @NotNull
    @Email
    @Size(max = 190)
    private String email = "";

    public String getEmail() {
        return email;
    }

    public void setEmail(String value) {
        email = value;
    }

    @NotBlank
    @Size(max = 60)
    private String documentNumber = "";

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String value) {
        documentNumber = value;
    }

    @NotNull
    private LocalDate documentDate = com.foresight.fcms.shared.time.ApplicationCalendar.today();

    public LocalDate getDocumentDate() {
        return documentDate;
    }

    public void setDocumentDate(LocalDate value) {
        documentDate = value;
    }

    @NotNull
    private LocalDate validUntil =
            com.foresight.fcms.shared.time.ApplicationCalendar.today().plusDays(30);

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDate value) {
        validUntil = value;
    }

    @NotBlank
    @Size(max = 3)
    private String currency = "TZS";

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String value) {
        currency = value;
    }

    @NotNull
    @Size(max = 2000)
    private String terms = "";

    public String getTerms() {
        return terms;
    }

    public void setTerms(String value) {
        terms = value;
    }

    @NotNull
    @Size(max = 250)
    private String billingAddress = "";

    public String getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(String value) {
        billingAddress = value;
    }

    @NotNull
    @Size(max = 150)
    private String billingCity = "";

    public String getBillingCity() {
        return billingCity;
    }

    public void setBillingCity(String value) {
        billingCity = value;
    }

    @NotNull
    @Size(max = 60)
    private String billingTin = "";

    public String getBillingTin() {
        return billingTin;
    }

    public void setBillingTin(String value) {
        billingTin = value;
    }

    @NotBlank
    @Size(max = 16)
    private String paymentMethod = "BANK_TRANSFER";

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String value) {
        paymentMethod = value;
    }

    @Min(0)
    private long version = 0;

    public long getVersion() {
        return version;
    }

    public void setVersion(long value) {
        version = value;
    }

    @NotNull private UUID requestKey = UUID.randomUUID();

    public UUID getRequestKey() {
        return requestKey;
    }

    public void setRequestKey(UUID value) {
        requestKey = value;
    }

    @NotNull
    @Size(min = 1, max = 50)
    private List<@Valid SalesItem> items = new ArrayList<>(List.of(new SalesItem()));

    public List<SalesItem> getItems() {
        return items;
    }

    public void setItems(List<SalesItem> value) {
        items = value;
    }
}
