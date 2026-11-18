<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<spring:htmlEscape defaultHtmlEscape="true" />
<!doctype html>
<html lang="${pageContext.response.locale.language}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title><spring:message code="error.title" /> | FCMS</title>
<link rel="stylesheet" href="<c:url value='/css/app.css'/>">
<link rel="stylesheet" href="<c:url value='/css/workspaces.css?v=20261117-editable-permissions1'/>">
<link rel="stylesheet" href="<c:url value='/css/notifications.css?v=20261117-cloudscape-notices1'/>">
</head>
<body>
<main class="app-auth-shell">
<section class="app-auth-card" aria-labelledby="error-title">
<div class="app-auth-title">FCMS</div>
<h1 id="error-title" class="app-page-title"><spring:message code="error.title" /></h1>
<c:if test="${not empty errorStatus}"><div><spring:message code="error.status" arguments="${errorStatus}" /></div></c:if>
<div class="fcms-alert" role="alert"><spring:message code="${errorCode}" /></div>
<div class="fcms-form-actions">
<c:choose>
<c:when test="${not empty pageContext.request.userPrincipal}">
<a class="app-btn btn-neutral" href="<c:url value='/dashboard'/>"><spring:message code="error.dashboard" /></a>
<c:if test="${logoutRejected}">
<form method="post" action="<c:url value='/logout'/>">
<input type="hidden" name="<c:out value='${_csrf.parameterName}'/>" value="<c:out value='${_csrf.token}'/>">
<button class="app-btn btn-neutral" type="submit"><spring:message code="error.retryLogout" /></button>
</form>
</c:if>
</c:when>
<c:otherwise>
<a class="app-btn btn-neutral" href="<c:url value='/login'/>"><spring:message code="auth.signIn" /></a>
</c:otherwise>
</c:choose>
</div>
</section>
</main>
</body>
</html>
