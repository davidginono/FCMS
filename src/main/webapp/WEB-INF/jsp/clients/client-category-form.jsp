<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<c:out value="${formTitle}"/>
</h1>
</div>
<c:url var="saveUrl" value="${formAction}" />
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true" enctype="multipart/form-data">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<form:hidden path="version"/>
<fieldset class="fcms-form-section">
<legend>
<spring:message code="common.details" />
</legend>
<div class="fcms-field">
<label for="clientName">
<spring:message code="common.name" />
</label>
<form:input id="clientName" path="clientName" type="text" />
<form:errors path="clientName" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="region">
<spring:message code="client.region" />
</label>
<form:input id="region" path="region" type="text" />
<form:errors path="region" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="entryDate">
<spring:message code="client.entryDate" />
</label>
<form:input id="entryDate" path="entryDate" type="date" />
<form:errors path="entryDate" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="expireDate">
<spring:message code="client.expireDate" />
</label>
<form:input id="expireDate" path="expireDate" type="date" />
<form:errors path="expireDate" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="financialYearId">
<spring:message code="report.year" />
</label>
<form:select id="financialYearId" path="financialYearId">
<c:forEach items="${financialYears}" var="option">
<form:option value="${option.id}">
<c:out value="${option.fyear}" />
</form:option>
</c:forEach>
</form:select>
<form:errors path="financialYearId" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="paymentStatusId">
<spring:message code="common.status" />
</label>
<form:select id="paymentStatusId" path="paymentStatusId">
<c:forEach items="${paymentStatuses}" var="option">
<form:option value="${option.id}">
<c:out value="${option.name}" />
</form:option>
</c:forEach>
</form:select>
<form:errors path="paymentStatusId" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="contractUpload">
<spring:message code="client.contract" />
</label>
<input type="file" id="contractUpload" name="contractUpload" accept="application/pdf,.pdf">
</div>
</fieldset>
<fieldset class="fcms-form-section">
<legend>
<spring:message code="client.billing" />
</legend>
<div class="fcms-field">
<label for="annualFee">
<spring:message code="client.annualFee" />
</label>
<form:input id="annualFee" path="annualFee" type="number" step="0.01" min="0" />
<form:errors path="annualFee" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="amount">
<spring:message code="client.amountPaid" />
</label>
<form:input id="amount" path="amount" type="number" step="0.01" min="0" />
<form:errors path="amount" cssClass="fcms-error" />
</div>
</fieldset>
<details class="fcms-form-section">
<summary>
<spring:message code="client.fees" />
</summary>
<c:if test="${category.withholdingTaxSupported}">
<div class="fcms-field">
<label for="withholdingTax">
<spring:message code="client.withholdingTax" />
</label>
<form:input id="withholdingTax" path="withholdingTax" type="number" step="0.01" min="0" />
<form:errors path="withholdingTax" cssClass="fcms-error" />
</div>
</c:if>
<c:if test="${category.commissionSupported}">
<div class="fcms-field">
<label for="commission">
<spring:message code="client.commission" />
</label>
<form:input id="commission" path="commission" type="number" step="0.01" min="0" />
<form:errors path="commission" cssClass="fcms-error" />
</div>
</c:if>
<div class="fcms-field">
<label for="programmerCost">
<spring:message code="client.programmerCost" />
</label>
<form:input id="programmerCost" path="programmerCost" type="number" step="0.01" min="0" />
<form:errors path="programmerCost" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="systemInstallFee">
<spring:message code="client.systemInstallFee" />
</label>
<form:input id="systemInstallFee" path="systemInstallFee" type="number" step="0.01" min="0" />
<form:errors path="systemInstallFee" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="trainingFee">
<spring:message code="client.trainingFee" />
</label>
<form:input id="trainingFee" path="trainingFee" type="number" step="0.01" min="0" />
<form:errors path="trainingFee" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="androidFee">
<spring:message code="client.androidFee" />
</label>
<form:input id="androidFee" path="androidFee" type="number" step="0.01" min="0" />
<form:errors path="androidFee" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="systemReinstallFee">
<spring:message code="client.systemReinstallFee" />
</label>
<form:input id="systemReinstallFee" path="systemReinstallFee" type="number" step="0.01" min="0" />
<form:errors path="systemReinstallFee" cssClass="fcms-error" />
</div>
<c:if test="${category.bulkSmsSupported}">
<div class="fcms-field">
<label for="bulkSmsFee">
<spring:message code="client.bulkSmsFee" />
</label>
<form:input id="bulkSmsFee" path="bulkSmsFee" type="number" step="0.01" min="0" />
<form:errors path="bulkSmsFee" cssClass="fcms-error" />
</div>
</c:if>
<c:if test="${category.hostingSupported}">
<div class="fcms-field">
<label for="hostingFee">
<spring:message code="client.hostingFee" />
</label>
<form:input id="hostingFee" path="hostingFee" type="number" step="0.01" min="0" />
<form:errors path="hostingFee" cssClass="fcms-error" />
</div>
</c:if>
</details>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert" />
<div class="fcms-form-actions">
<a class="app-btn btn-neutral" href="<c:url value='${category.route}' />">
<spring:message code="common.cancel" />
</a>
<button class="app-btn btn-launch" type="submit">
<spring:message code="common.save" />
</button>
</div>
</form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
