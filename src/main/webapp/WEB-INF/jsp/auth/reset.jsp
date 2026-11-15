<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<!doctype html>
<html lang="${pageContext.response.locale.language}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>FCMS</title>
<link rel="stylesheet" href="<c:url value='/css/app.css'/>">
<link rel="stylesheet" href="<c:url value='/css/workspaces.css'/>">
</head>
<body>
<main class="app-main fcms-form">
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="password.reset" />
</h1>
</div>
<c:if test="${param.requested!=null}">
<div role="status">
<spring:message code="password.requested" />
</div>
</c:if>
<c:set var="formAction" value="/password/reset"/>
<c:url var="saveUrl" value="${formAction}" />
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<fieldset class="fcms-form-section">
<legend><spring:message code="password.verification"/></legend>
<div class="fcms-field">
<label for="token">
<spring:message code="password.token" />
</label>
<form:input id="token" path="token" type="text" autocomplete="off" maxlength="100" required="required"/>
<form:errors path="token" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="otp">
<spring:message code="password.otp" />
</label>
<form:input id="otp" path="otp" type="text" inputmode="numeric" autocomplete="one-time-code" maxlength="6" pattern="[0-9]{6}" required="required"/>
<form:errors path="otp" cssClass="fcms-error" />
</div>
</fieldset>
<fieldset class="fcms-form-section">
<legend><spring:message code="common.password"/></legend>
<div class="fcms-field">
<label for="password">
<spring:message code="account.newPassword" />
</label>
<form:password id="password" path="password" autocomplete="new-password" minlength="12" maxlength="72" required="required"/>
<form:errors path="password" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="confirmPassword">
<spring:message code="common.confirmPassword" />
</label>
<form:password id="confirmPassword" path="confirmPassword" autocomplete="new-password" minlength="12" maxlength="72" required="required"/>
<form:errors path="confirmPassword" cssClass="fcms-error" />
</div>
</fieldset>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert" />
<div class="fcms-form-actions">
<a class="app-btn btn-neutral" href="<c:url value='/login' />">
<spring:message code="common.cancel" />
</a>
<button class="app-btn btn-launch" type="submit">
<spring:message code="common.submit" />
</button>
</div>
</form:form>
</main>
</body>
</html>