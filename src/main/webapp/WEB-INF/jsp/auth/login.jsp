<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<!doctype html>
<html lang="${pageContext.response.locale.language}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>FCMS</title>
<link rel="stylesheet" href="<c:url value='/css/app.css'/>">
<link rel="stylesheet" href="<c:url value='/css/workspaces.css?v=20261116-password-eye1'/>">
<script defer src="<c:url value='/js/password-visibility.js?v=20261116-password-eye1'/>"></script>
<link rel="stylesheet" href="<c:url value='/css/notifications.css?v=20261117-cloudscape-notices1'/>">
</head>
<body>
<main class="app-auth-shell">
<section class="app-auth-card">
<h1 class="app-auth-title">FCMS</h1>
<h2>
<spring:message code="auth.title" />
</h2>
<c:if test="${param.error!=null}">
<div class="fcms-alert" role="alert">
<spring:message code="auth.failed" />
</div>
</c:if>
<c:if test="${param.logout!=null}">
<div class="fcms-alert" data-message-type="success" role="status">
<spring:message code="auth.loggedOut" />
</div>
</c:if>
<c:if test="${param.passwordChanged!=null}">
<div class="fcms-alert" data-message-type="success" role="status">
<spring:message code="auth.changed" />
</div>
</c:if>
<form method="post" action="<c:url value='/login'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<div class="fcms-field">
<label for="email">
<spring:message code="common.email" />
</label>
<input type="email" id="email" name="email" maxlength="100" required autocomplete="username">
</div>
<div class="fcms-field">
<label for="password">
<spring:message code="common.password" />
</label>
<div class="fcms-password-control" data-password-visibility>
<input type="password" id="password" name="password" required autocomplete="current-password">
<ui:passwordToggle inputId="password" />
</div>
</div>
<div class="fcms-form-actions">
<button class="app-btn btn-neutral" type="submit">
<spring:message code="auth.signIn" />
</button>
</div>
</form>
<a href="<c:url value='/password/forgot'/>">
<spring:message code="password.forgot" />
</a>
<div>
<a href="?lang=en">English</a> · <a href="?lang=sw">Kiswahili</a>
</div>
</section>
</main>
</body>
</html>
