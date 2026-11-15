package com.foresight.fcms.client.saccos.dto;

public class SaccosFilter {
    private int size = 25;
    private String sort = "name";
    private String direction = "asc";

    public int getSize() {
        return size;
    }

    public void setSize(int s) {
        size = java.util.Set.of(10, 25, 50, 100).contains(s) ? s : 25;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String s) {
        sort = s;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String d) {
        direction = "desc".equals(d) ? "desc" : "asc";
    }

    private Long financialYearId;

    private Long paymentStatusId;

    private String search;

    private int page;

    private String view = "list";

    private String reportType = "full";

    public Long getFinancialYearId() {
        return financialYearId;
    }

    public void setFinancialYearId(Long financialYearId) {
        this.financialYearId = financialYearId;
    }

    public Long getPaymentStatusId() {
        return paymentStatusId;
    }

    public void setPaymentStatusId(Long paymentStatusId) {
        this.paymentStatusId = paymentStatusId;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search == null ? null : search.substring(0, Math.min(search.length(), 100));
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, Math.min(page, 10000));
    }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public String getReportType() {
        return reportType;
    }

    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    public boolean isRevenueView() {
        return "revenue".equalsIgnoreCase(view == null ? "" : view.trim());
    }

    public String normalizedView() {
        return isRevenueView() ? "revenue" : "list";
    }

    public String normalizedSearch() {
        if (search == null || search.isBlank()) {
            return null;
        }
        return search.trim();
    }
}
