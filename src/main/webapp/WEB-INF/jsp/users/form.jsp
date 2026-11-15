<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="${formTitle}" />
</h1>
</div>
<c:url var="saveUrl" value="${formAction}" />
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<form:hidden path="version" />
<fieldset class="fcms-form-section">
<legend>
<spring:message code="common.details" />
</legend>
<div class="fcms-field">
<label for="firstName">
<spring:message code="common.firstName" />
</label>
<form:input id="firstName" path="firstName" type="text" />
<form:errors path="firstName" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="lastName">
<spring:message code="common.lastName" />
</label>
<form:input id="lastName" path="lastName" type="text" />
<form:errors path="lastName" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="email">
<spring:message code="common.email" />
</label>
<form:input id="email" path="email" type="email" />
<form:errors path="email" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="roleId">
<spring:message code="common.role" />
</label>
<form:select id="roleId" path="roleId">
<c:forEach items="${roles}" var="option">
<form:option value="${option.id}">
<c:out value="${option.name}" />
</form:option>
</c:forEach>
</form:select>
<form:errors path="roleId" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="statusId">
<spring:message code="common.status" />
</label>
<form:select id="statusId" path="statusId">
<c:forEach items="${statuses}" var="option">
<form:option value="${option.id}">
<c:out value="${option.name}" />
</form:option>
</c:forEach>
</form:select>
<form:errors path="statusId" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="password">
<spring:message code="common.password" />
</label>
<form:password id="password" path="password" autocomplete="new-password" minlength="12" maxlength="72"/>
<form:errors path="password" cssClass="fcms-error" />
</div>
</fieldset>
<fieldset class="fcms-form-section">
<legend>
<spring:message code="users.claims" />
</legend>
<div class="fcms-checks">
<c:forEach items="${availableClaims}" var="claim">
<label>
<form:checkbox path="claims" value="${claim}" />
<c:out value="${claim}" />
</label>
</c:forEach>
</div>
</fieldset>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert" />
<div class="fcms-form-actions">
<a class="app-btn btn-neutral" href="<c:url value='/admin/users' />">
<spring:message code="common.cancel" />
</a>
<button class="app-btn btn-launch" type="submit">
<spring:message code="${formPrimaryAction}" />
</button>
</div>
</form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
