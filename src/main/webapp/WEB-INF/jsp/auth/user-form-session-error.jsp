<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<!doctype html>
<html lang="${pageContext.response.locale.language}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>FCMS</title>
<link rel="stylesheet" href="<c:url value='/css/app.css'/>">
<link rel="stylesheet" href="<c:url value='/css/workspaces.css?v=20261117-editable-permissions1'/>">
<link rel="stylesheet" href="<c:url value='/css/notifications.css?v=20261117-cloudscape-notices1'/>">
</head>
<body>
<main class="app-auth-shell">
<section class="app-auth-card">
<h1 class="app-auth-title">FCMS</h1>
<h2><spring:message code="users.sessionErrorTitle" /></h2>
<div class="fcms-alert" role="alert"><spring:message code="users.sessionError" /></div>
<div class="fcms-form-actions">
<a class="app-btn btn-neutral" href="<c:url value='/login'/>" target="_blank" rel="noopener"><spring:message code="auth.signIn" /></a>
<a class="app-btn btn-neutral" href="<c:url value='${userFormRecoveryUrl}'/>"><spring:message code="users.reopenForm" /></a>
</div>
</section>
</main>
</body>
</html>
