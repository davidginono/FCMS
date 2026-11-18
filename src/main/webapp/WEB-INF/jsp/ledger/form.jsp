<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="${empty form.operation?kind.title:'ledger.operation.'.concat(form.operation)}" />
</h1>
</div>
<c:url var="saveUrl" value="${formAction}" />
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-create="true" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<form:hidden path="requestKey"/><form:hidden path="operation"/>
<section class="erp-resource-section" aria-labelledby="ledger-form-section-1">
<h2 id="ledger-form-section-1">
<spring:message code="common.details" />
</h2><div class="fcms-section-body">
<div class="fcms-field">
<label for="date">
<spring:message code="common.date" />
</label>
<form:input id="date" path="date" type="date" />
<form:errors path="date" cssClass="fcms-error" />
</div>
<c:choose><c:when test="${kind.name()=='BULK'}"><form:hidden path="category"/></c:when><c:otherwise><div class="fcms-field">
<label for="category">
<spring:message code="ledger.category" />
</label>
<form:input id="category" path="category" type="text" />
<form:errors path="category" cssClass="fcms-error" />
</div></c:otherwise></c:choose>
<div class="fcms-field">
<label for="description">
<spring:message code="common.description" />
</label>
<form:textarea id="description" path="description" />
<form:errors path="description" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="reference">
<spring:message code="common.reference" />
</label>
<form:input id="reference" path="reference" type="text" />
<form:errors path="reference" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="paymentMode">
<spring:message code="ledger.paymentMode" />
</label>
<form:select id="paymentMode" path="paymentMode">
<form:option value="CASH">
<spring:message code="status.CASH" text="CASH"/>
</form:option>
<form:option value="BANK">
<spring:message code="status.BANK" text="BANK"/>
</form:option>
<form:option value="MOBILE">
<spring:message code="status.MOBILE" text="MOBILE"/>
</form:option>
<form:option value="CHEQUE">
<spring:message code="status.CHEQUE" text="CHEQUE"/>
</form:option>
</form:select>
<form:errors path="paymentMode" cssClass="fcms-error" />
</div>
<c:if test="${kind.name()=='CLOUD'}">
<div class="fcms-field">
<label for="direction">
<spring:message code="ledger.direction" />
</label>
<form:select id="direction" path="direction">
<form:option value="IN">
<spring:message code="ledger.in" />
</form:option>
<form:option value="OUT">
<spring:message code="ledger.out" />
</form:option>
</form:select>
<form:errors path="direction" cssClass="fcms-error" />
</div>
</c:if>
<div class="fcms-field">
<label for="amount">
<spring:message code="common.amount" />
</label>
<form:input id="amount" path="amount" type="number" step="0.01" min="${kind.name()=='BULK'?'0':'0.01'}" />
<form:errors path="amount" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="clientId">
<spring:message code="common.client" />
</label>
<form:select id="clientId" path="clientId" data-choice-url="${pageContext.request.contextPath}/lookups/clients">
<form:option value="">—</form:option>
<c:forEach items="${clients}" var="option">
<form:option value="${option.id}">
<c:out value="${option.name}" />
</form:option>
</c:forEach>
</form:select>
<form:errors path="clientId" cssClass="fcms-error" />
</div>
<c:if test="${kind.name()=='BULK'}">
<div class="fcms-field">
<label for="units">
<spring:message code="ledger.units" />
</label>
<form:input id="units" path="units" type="number" min="0" step="1" />
<form:errors path="units" cssClass="fcms-error" />
</div>
</c:if>
</div></section>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert" />
<form:errors path="requestKey" element="div" cssClass="fcms-alert" role="alert"/><div class="fcms-form-actions">
<a class="app-btn btn-neutral" href="<c:url value='${baseUrl}' />">
<spring:message code="common.cancel" />
</a>
<button class="app-btn btn-launch" type="submit">
<spring:message code="common.submit" />
</button>
</div>
</form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
