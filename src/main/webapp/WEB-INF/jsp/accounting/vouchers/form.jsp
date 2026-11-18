<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>

<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<link rel="stylesheet" href="<c:url value='/css/accounting-entry.css?v=20261118-split2'/>">

<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a href="<c:url value='${baseUrl}'/>"><spring:message code="voucher.register.${voucherKind}"/></a><span aria-hidden="true">›</span><span aria-current="page"><spring:message code="voucher.title.${voucherKind}"/></span></nav>

<div class="app-page-header"><h1 class="app-page-title"><spring:message code="voucher.title.${voucherKind}"/></h1></div>

<spring:message code="voucher.chooseTransaction" var="voucherChooseTransaction"/><spring:message code="voucher.error.lookup" var="voucherLookupError"/><spring:message code="voucher.error.rowLimit" var="voucherRowLimit"/>

<c:url var="postUrl" value="${baseUrl}"/>

<form:form modelAttribute="voucherForm" method="post" action="${postUrl}" cssClass="fcms-form voucher-form" data-resource-create="true" data-resource-form-page="true" data-financial-form="true" data-voucher-form="true" data-voucher-base="${postUrl}" data-kind="${voucherKind}" data-choose-transaction="${voucherChooseTransaction}" data-lookup-error="${voucherLookupError}" data-row-limit="${voucherRowLimit}">

<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><form:hidden path="requestKey"/>

<section class="erp-resource-section"><h2><spring:message code="voucher.information"/></h2><div class="fcms-section-body"><div class="voucher-grid">

<div class="fcms-field"><label for="activityId"><spring:message code="config.activity"/></label><form:select path="activityId" id="activityId" data-voucher-activity="true" data-choice-url="${pageContext.request.contextPath}/accounting/config/activities/choices"><form:option value=""><spring:message code="voucher.manual"/></form:option><form:options items="${activities}" itemValue="id" itemLabel="name"/></form:select><form:errors path="activityId" cssClass="fcms-error"/></div>

<ui:field path="date" label="common.date" type="date"/>

<div class="fcms-field"><label for="voucher-number"><spring:message code="gl.voucherId"/></label><output id="voucher-number" data-voucher-number role="status" aria-live="polite"></output><button type="button" class="app-btn btn-neutral" data-number-retry hidden><spring:message code="voucher.retry"/></button><span data-number-error class="voucher-number-error" role="alert" hidden><spring:message code="voucher.error.number"/></span></div>

<c:if test="${voucherKind!='JOURNAL'}"><ui:field path="party" label="${voucherKind=='RECEIPT'?'voucher.receivedFrom':'voucher.paidTo'}"/>

</c:if>

<ui:field path="reference" label="common.reference"/>

<div class="fcms-field"><label for="clientKey"><spring:message code="common.client"/></label><form:select path="clientKey" id="clientKey" data-choice-url="${pageContext.request.contextPath}${baseUrl}/clients"><form:option value=""/><c:if test="${not empty selectedClient}"><form:option value="${selectedClient.id()}"><c:out value="${selectedClient.name()}"/></form:option></c:if></form:select><form:errors path="clientKey" cssClass="fcms-error"/></div>

<div class="fcms-field"><label for="cashFlow"><spring:message code="gl.cashFlowClassification"/></label><form:select path="cashFlow" id="cashFlow"><c:forTokens items="NONE,OPERATING,INVESTING,FINANCING,INTERNAL" delims="," var="flow"><form:option value="${flow}"><spring:message code="gl.flow.${flow}"/></form:option></c:forTokens></form:select><form:errors path="cashFlow" cssClass="fcms-error"/></div>

</div><details class="voucher-additional" ${not empty voucherForm.description or not empty voucherForm.evidenceReference?'open':''}><summary><spring:message code="voucher.additional"/></summary><ui:field path="description" label="common.description"/><ui:field path="evidenceReference" label="gl.evidence"/></details></div></section>

<section class="erp-resource-section"><h2 class="voucher-section-toolbar"><span><spring:message code="voucher.transactions"/></span><button type="button" class="app-btn btn-neutral" data-add-voucher-row><spring:message code="voucher.addTransaction"/></button></h2><div class="fcms-section-body"><div data-voucher-rows><c:forEach items="${voucherForm.rows}" var="row" varStatus="n"><%@ include file="row.jspf" %></c:forEach></div><form:errors path="rows" cssClass="fcms-error"/><div class="voucher-total"><spring:message code="voucher.total"/> (TZS): <output data-voucher-total>0.00</output></div><div data-voucher-status role="status"></div></div></section>

<c:if test="${not empty preview}"><section class="erp-resource-section" data-voucher-preview tabindex="-1"><h2><spring:message code="voucher.preview"/></h2><div class="fcms-section-body"><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th>#</th><th><spring:message code="gl.mapping.debitAccount"/></th><th><spring:message code="gl.mapping.creditAccount"/></th><th><spring:message code="common.amount"/></th></tr></thead><tbody><c:forEach items="${preview.rows()}" var="r"><c:forEach items="${r.postings()}" var="entry"><tr><td>${r.ordinal()}</td><td><c:if test="${entry.side()=='DEBIT'}"><c:out value="${entry.account().name()}"/></c:if></td><td><c:if test="${entry.side()=='CREDIT'}"><c:out value="${entry.account().name()}"/></c:if></td><td><fmt:formatNumber value="${entry.amount()}" minFractionDigits="2" maxFractionDigits="2"/></td></tr></c:forEach></c:forEach></tbody></table></div><div class="voucher-total"><spring:message code="voucher.total"/> (TZS): <fmt:formatNumber value="${preview.total()}" minFractionDigits="2" maxFractionDigits="2"/></div></div></section></c:if>

<form:errors path="" cssClass="fcms-alert" element="div" role="alert"/><form:errors path="requestKey" cssClass="fcms-alert" element="div" role="alert"/>

<div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='${baseUrl}'/>"><spring:message code="common.cancel"/></a><button class="app-btn btn-neutral" type="submit" name="action" value="draft"><spring:message code="voucher.saveDraft"/></button><button class="app-btn btn-neutral" type="submit" name="action" value="preview"><spring:message code="voucher.preview"/></button><button class="app-btn btn-launch" type="submit" name="action" value="submit"><spring:message code="voucher.submit"/></button></div>

<template data-voucher-entry-template><div class="voucher-split-entry" data-split-entry><input type="hidden" data-entry-field="side"><div class="fcms-field"><label data-entry-account-label></label><select data-entry-field="accountId" required><option value=""></option></select></div><div class="fcms-field"><label data-entry-amount-label><spring:message code="common.amount"/></label><input data-entry-field="amount" type="number" min="0.01" max="999999999999.99" step="0.01" inputmode="decimal" required></div><button type="button" class="app-btn btn-neutral" data-remove-entry><spring:message code="common.remove"/></button></div></template>
<span hidden data-debit-label><spring:message code="gl.mapping.debitAccount"/></span><span hidden data-credit-label><spring:message code="gl.mapping.creditAccount"/></span>
</form:form><script defer src="<c:url value='/js/accounting-entry.js?v=20261118-modes2'/>"></script>

<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
