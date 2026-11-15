<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="error.title" />
</h1>
</div>
<div class="fcms-alert" role="alert">
<spring:message code="${errorCode}" />
</div>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
