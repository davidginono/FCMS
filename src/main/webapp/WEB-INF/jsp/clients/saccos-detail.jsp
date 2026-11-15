<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<div class="app-page-header">
    <div>
        <h1 class="app-page-title">
<c:out value="${saccos.saccosName()}" />
</h1>
    </div>
    <div class="app-actions">
        <a class="app-btn btn-neutral" href="<c:url value='/clients/saccos' />">
            <span class="app-icon" data-lucide="arrow-left" aria-hidden="true">
</span>
            <span><spring:message code="common.back"/></span>
        </a>
        <c:if test="${permissions.contains('client.write')}"><a class="app-btn btn-primary" href="<c:url value='/clients/saccos/${saccos.id()}/edit' />">
            <span class="app-icon" data-lucide="edit-3" aria-hidden="true">
</span>
            <span><spring:message code="common.edit"/></span>
        </a></c:if>
        <c:if test="${permissions.contains('client.write')}"><a class="app-btn btn-neutral" href="<c:url value='/clients/saccos/${saccos.id()}/archive'/>"><spring:message code="client.archiveAction"/></a></c:if>
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

<section class="app-card">
    <div class="app-card-header">
        <h2 class="app-section-title"><spring:message code="client.billing"/></h2>
    </div>
    <div class="app-card-body">
        <div class="app-definition-grid">
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.entryDate"/></span>
<div class="app-definition-value">
<c:out value="${appTime:display(saccos.entryDate())}" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.expireDate"/></span>
<div class="app-definition-value">
<c:out value="${appTime:display(saccos.expireDate())}" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="common.status"/></span>
<div class="app-definition-value">
<span class="app-badge">
<spring:message code="status.${saccos.paymentStatus().toUpperCase()}" text="${saccos.paymentStatus()}"/>
</span>
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.invoiceStatus"/></span>
<div class="app-definition-value">
<span class="app-badge">
<spring:message code="status.${saccos.invoiceStatus().toUpperCase()}" text="${saccos.invoiceStatus()}"/>
</span>
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.annualFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.annualFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.amountPaid"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.amount()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.datePaid"/></span>
<div class="app-definition-value">
<c:out value="${appTime:display(saccos.datePaid())}" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.amountRemaining"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.amountRemaining()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.withholdingTax"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.withholdingTax()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.commission"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.commission()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.programmerCost"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.programmerCost()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.operationalStatus"/></span>
<div class="app-definition-value">
<span class="app-badge">
<spring:message code="status.${saccos.operationalStatus().toUpperCase()}" text="${saccos.operationalStatus()}"/>
</span>
</div>
</div>
        </div>
    </div>
</section>

<section class="app-card mt-5">
    <div class="app-card-header">
        <h2 class="app-section-title"><spring:message code="client.fees"/></h2>
    </div>
    <div class="app-card-body">
        <div class="app-definition-grid">
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.systemInstallFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.systemInstallFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.trainingFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.trainingFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.androidFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.androidFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.androidCost"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.androidCost()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.systemReinstallFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.systemReinstallFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.bulkSmsFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.bulkSmsFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.hostingFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.hostingFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.essFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.essFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.cloudBackupFee"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.cloudBackupFee()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
<span class="fcms-label"><spring:message code="client.cloudBackupCost"/></span>
<div class="app-definition-value">
<fmt:formatNumber value="${saccos.cloudBackupCost()}" minFractionDigits="2" maxFractionDigits="2" />
</div>
</div>
            <div class="app-definition-item">
                <span class="fcms-label"><spring:message code="client.contract"/></span>
                <div class="app-definition-value">
                    <c:choose>
                        <c:when test="${saccos.hasContractFile()}">
                            <a class="font-semibold text-blue-700 hover:underline" href="<c:url value='/clients/saccos/${saccos.id()}/contract' />"><spring:message code="client.openContract"/></a>
                        </c:when>
                        <c:otherwise>
<span class="text-gray-500"><spring:message code="client.noContract"/></span>
</c:otherwise>
                    </c:choose>
                </div>
            </div>
        </div>
    </div>
</section>

<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
