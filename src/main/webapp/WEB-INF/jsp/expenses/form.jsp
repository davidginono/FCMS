<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="nav.expenses" />
</h1>
</div>
<c:url var="saveUrl" value="${formAction}" />
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<form:hidden path="version"/>
<form:hidden path="requestKey"/>
<fieldset class="fcms-form-section">
<legend>
<spring:message code="common.details" />
</legend>
<div class="fcms-field">
<label for="date">
<spring:message code="common.date" />
</label>
<form:input id="date" path="date" type="date" />
<form:errors path="date" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="category">
<spring:message code="ledger.category" />
</label>
<form:input id="category" path="category" type="text" />
<form:errors path="category" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="description">
<spring:message code="common.description" />
</label>
<form:textarea id="description" path="description" />
<form:errors path="description" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="payee">
<spring:message code="expense.payee" />
</label>
<form:input id="payee" path="payee" type="text" />
<form:errors path="payee" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="amount">
<spring:message code="common.amount" />
</label>
<form:input id="amount" path="amount" type="number" step="0.01" min="0.01" />
<form:errors path="amount" cssClass="fcms-error" />
</div>
</fieldset>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert" />
<div class="fcms-form-actions">
<a class="app-btn btn-neutral" href="<c:url value='/finance/expenses' />">
<spring:message code="common.cancel" />
</a>
<button class="app-btn btn-launch" type="submit">
<spring:message code="common.save" />
</button>
</div>
</form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
