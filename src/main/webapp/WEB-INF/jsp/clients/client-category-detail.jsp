<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<div class="app-page-header">
    <div>
        <h1 class="app-page-title">
<c:out value="${client.clientName()}" />
</h1>
    </div>
    <div class="app-actions">
        <a class="app-btn btn-neutral" href="<c:url value='${category.route}' />">
            <span class="app-icon" data-lucide="arrow-left" aria-hidden="true">
</span>
            <span><spring:message code="common.back"/></span>
        </a>
        <c:if test="${permissions.contains('client.write')}"><a class="app-btn btn-primary" href="<c:url value='${category.route}/${client.id()}/edit' />">
            <span class="app-icon" data-lucide="edit-3" aria-hidden="true">
</span>
            <span><spring:message code="common.edit"/></span>
        </a></c:if>
    </div>
</div>

<c:if test="${not empty successMessage}">
    <div class="app-alert app-alert-success">
        <span class="app-icon" data-lucide="check-circle-2" aria-hidden="true">
</span>
        <span>
<spring:message code="common.saved"/>
</span>
    </div>
</c:if>

<section class="app-form-section">
    <div class="mb-4">
        <h2 class="app-section-title"><spring:message code="common.details"/></h2>
    </div>
    <div class="app-definition-grid">
        <div class="app-definition-item">
<span class="fcms-label">
<spring:message code="common.name"/>
</span>
<div class="app-definition-value">
<c:out value="${client.clientName()}" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.region"/></span>
<div class="app-definition-value">
<c:out value="${client.region()}" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.entryDate"/></span>
<div class="app-definition-value">
<c:out value="${appTime:display(client.entryDate())}" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.expireDate"/></span>
<div class="app-definition-value">
<c:out value="${appTime:display(client.expireDate())}" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="common.year"/></span>
<div class="app-definition-value">
<c:out value="${client.financialYear()}" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="common.status"/></span>
<div class="app-definition-value">
<spring:message code="status.${client.paymentStatus().toUpperCase()}" text="${client.paymentStatus()}"/>
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.annualFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.annualFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.amountPaid"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.amount()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        <c:if test="${category.withholdingTaxSupported}">
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.withholdingTax"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.withholdingTax()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        </c:if>
        <c:if test="${category.commissionSupported}">
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.commission"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.commission()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        </c:if>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.programmerCost"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.programmerCost()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.systemInstallFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.systemInstallFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.trainingFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.trainingFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.androidFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.androidFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.systemReinstallFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.systemReinstallFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        <c:if test="${category.bulkSmsSupported}">
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.bulkSmsFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.bulkSmsFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        </c:if>
        <c:if test="${category.hostingSupported}">
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.hostingFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.hostingFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        </c:if>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.revenueTotal"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.revenueTotal()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
        <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.grossProfit"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${client.grossProfit()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
    </div>
</section>

<section class="app-form-actions mt-4">
    <div class="app-actions">
        <c:if test="${client.hasContractFile()}">
            <a class="app-btn btn-neutral" href="<c:url value='${category.route}/${client.id()}/contract' />" target="_blank" rel="noopener">
                <span class="app-icon" data-lucide="file-text" aria-hidden="true">
</span>
                <span><spring:message code="client.openContract"/></span>
            </a>
        </c:if>
        <c:if test="${permissions.contains('client.write')}"><a class="app-btn btn-neutral" href="<c:url value='${category.route}/${client.id()}/archive'/>"><spring:message code="client.archiveAction"/></a></c:if>
    </div>
</section>

<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
