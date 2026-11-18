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
<link rel="stylesheet" href="<c:url value='/css/workspaces.css?v=20261117-form-sections1'/>">
<link rel="stylesheet" href="<c:url value='/css/notifications.css?v=20261117-cloudscape-notices1'/>">
</head>
<body>
<main class="app-main fcms-form">
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="password.forgot" />
</h1>
</div>
<c:set var="formAction" value="/password/forgot"/>
<c:url var="saveUrl" value="${formAction}" />
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<section class="erp-resource-section"><div class="fcms-section-body">
<div class="fcms-field">
<label for="email">
<spring:message code="common.email" />
</label>
<form:input id="email" path="email" type="email" />
<form:errors path="email" cssClass="fcms-error" />
</div>
</div></section>
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