<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="${formTitle}" />
</h1>
</div>
<div data-form-session-feedback hidden
 data-expired="<spring:message code='users.sessionExpired'/>"
 data-changed="<spring:message code='users.sessionChanged'/>"
 data-forbidden="<spring:message code='users.sessionForbidden'/>"
 data-unavailable="<spring:message code='users.sessionUnavailable'/>">
<div class="fcms-alert" role="alert" tabindex="-1" data-form-session-alert>
<span data-form-session-message></span>
<a href="<c:url value='/login'/>" target="_blank" rel="noopener" data-native-navigation data-form-session-signin hidden><spring:message code="auth.signIn" /></a>
</div>
</div>
<c:url var="saveUrl" value="${formAction}" />
<c:url var="formSessionUrl" value="/admin/users/form-session" />
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-create="${formAction == '/admin/users'}" data-resource-form-page="true" data-user-permissions="true" data-form-session-url="${formSessionUrl}" data-form-session-user="${actorId}">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<form:hidden path="version" />
<section class="erp-resource-section" aria-labelledby="users-form-section-1">
<h2 id="users-form-section-1">
<spring:message code="common.details" />
</h2><div class="fcms-section-body">
<div class="fcms-field">
<label for="firstName">
<spring:message code="common.firstName" />
</label>
<form:input id="firstName" path="firstName" type="text" required="true" maxlength="100" />
<form:errors path="firstName" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="lastName">
<spring:message code="common.lastName" />
</label>
<form:input id="lastName" path="lastName" type="text" required="true" maxlength="100" />
<form:errors path="lastName" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="email">
<spring:message code="common.email" />
</label>
<form:input id="email" path="email" type="email" required="true" maxlength="100" />
<form:errors path="email" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="roleId">
<spring:message code="common.role" />
</label>
<form:select id="roleId" path="roleId">
<c:forEach items="${roles}" var="option">
<form:option value="${option.id}" data-default-claims="${fn:escapeXml(option.defaults)}">
<c:out value="${option.name}" />
</form:option>
<c:if test="${option.id == form.roleId or (empty form.roleId and option.id == roles[0].id)}">
<c:set var="selectedDefaults" value=" ${option.defaults} " />
</c:if>
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
<div class="fcms-password-control" data-password-visibility>
<form:password id="password" path="password" autocomplete="new-password" minlength="12" maxlength="72"/>
<ui:passwordToggle inputId="password" />
</div>
<form:errors path="password" cssClass="fcms-error" />
</div>
</div></section>
<section class="erp-resource-section" aria-labelledby="users-form-section-2">
<h2 id="users-form-section-2">
<spring:message code="users.claims" />
</h2><div class="fcms-section-body">
<input type="hidden" name="_claims" value="on">
<input type="hidden" name="permissionsSubmitted" value="true">
<div data-error-scope="claims"><div class="fcms-checks">
<c:forEach items="${availableClaims}" var="claim" varStatus="permission">
<c:set var="claimToken" value=" ${claim} " />
<c:set var="inherited" value="${fn:contains(selectedDefaults, claimToken)}" />
<c:set var="checked" value="${form.permissionsSubmitted ? form.claims.contains(claim) : inherited or form.claims.contains(claim)}" />
<label>
<input type="checkbox" id="claim-${permission.index}" name="claims" value="${fn:escapeXml(claim)}" data-role-permission<c:if test="${checked}"> checked</c:if>>
<span class="fcms-permission-label">
<c:out value="${claim}" />
</span>
</label>
</c:forEach>
</div>
<form:errors path="claims" cssClass="fcms-error" /></div>
</div></section>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert" />
<form:errors path="version" element="div" cssClass="fcms-alert" role="alert"/><div class="fcms-form-actions">
<a class="app-btn btn-neutral" href="<c:url value='/admin/users' />">
<spring:message code="common.cancel" />
</a>
<button class="app-btn btn-launch" type="submit">
<spring:message code="${formPrimaryAction}" />
</button>
</div>
</form:form>
<script defer src="<c:url value='/js/user-permissions.js?v=20261117-editable-permissions1' />"></script>
<script defer src="<c:url value='/js/password-visibility.js?v=20261116-password-eye1' />"></script>
<script defer src="<c:url value='/js/user-form-session.js?v=20261117-cloudscape-notices1' />"></script>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
