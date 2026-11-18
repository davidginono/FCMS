package com.foresight.fcms.client.category.model;

import java.util.Arrays;

public enum ClientCategory {
    AMCOS("amcos", "amcos", "AMCOS", "AMCOS", "AMCOS Name", "", false, false, true, true),
    UNIONS(
            "unions",
            "unions",
            "UNION",
            "Union",
            "Union Name",
            "Legacy union client register, filtered by fiscal year and payment state.",
            true,
            true,
            true,
            true),
    MICROFINANCE(
            "microfinance",
            "microfinance_clients",
            "MICROFINANCE",
            "Microfinance",
            "Microfinance Name",
            "Legacy microfinance client register, filtered by fiscal year and payment state.",
            false,
            false,
            false,
            false),
    OTHERS(
            "others",
            "other_clients",
            "OTHERS",
            "Other Client",
            "Client Name",
            "Legacy other client register, filtered by fiscal year and payment state.",
            false,
            false,
            false,
            false);

    private final String slug;
    private final String tableName;
    private final String title;
    private final String singularLabel;
    private final String nameLabel;
    private final String description;
    private final boolean withholdingTaxSupported;
    private final boolean commissionSupported;
    private final boolean bulkSmsSupported;
    private final boolean hostingSupported;

    ClientCategory(
            String slug,
            String tableName,
            String title,
            String singularLabel,
            String nameLabel,
            String description,
            boolean withholdingTaxSupported,
            boolean commissionSupported,
            boolean bulkSmsSupported,
            boolean hostingSupported) {
        this.slug = slug;
        this.tableName = tableName;
        this.title = title;
        this.singularLabel = singularLabel;
        this.nameLabel = nameLabel;
        this.description = description;
        this.withholdingTaxSupported = withholdingTaxSupported;
        this.commissionSupported = commissionSupported;
        this.bulkSmsSupported = bulkSmsSupported;
        this.hostingSupported = hostingSupported;
    }

    public String getSlug() {
        return slug;
    }

    public String getTableName() {
        return tableName;
    }

    public String getTitle() {
        return title;
    }

    public String getSingularLabel() {
        return singularLabel;
    }

    public String getNameLabel() {
        return nameLabel;
    }

    public String getDescription() {
        return description;
    }

    public String getRoute() {
        return "/clients/" + slug;
    }

    public String getCreateLabel() {
        return "Create " + singularLabel;
    }

    public boolean isWithholdingTaxSupported() {
        return withholdingTaxSupported;
    }

    public boolean isCommissionSupported() {
        return commissionSupported;
    }

    public boolean isBulkSmsSupported() {
        return bulkSmsSupported;
    }

    public boolean isHostingSupported() {
        return hostingSupported;
    }

    public boolean isFinancialYearSupported() {
        return this != OTHERS;
    }

    public boolean isFinancialYearEditable() {
        return this == MICROFINANCE;
    }

    public boolean isOperationalStatusSupported() {
        return this != OTHERS;
    }

    public boolean isContractOnCreate() {
        return this == OTHERS;
    }

    public boolean isProgrammerCostOnCreate() {
        return this != MICROFINANCE;
    }

    public static ClientCategory fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(category -> category.slug.equals(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown client category"));
    }
}
