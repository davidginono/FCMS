<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<h1 class="app-page-title"><spring:message code="client.manageLifecycle"/></h1>
<c:url var="lifecycleUrl" value="${clientRoute}/${record.id()}/lifecycle"/>
<form class="fcms-form" method="post" action="${lifecycleUrl}">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<section class="erp-resource-section" aria-labelledby="lifecycle-heading">
<h2 id="lifecycle-heading"><c:out value="${record.name()}"/></h2>
<div class="fcms-section-body">
<c:if test="${not empty record.status()}"><dl class="app-definition-grid"><div class="app-definition-item"><dt><spring:message code="client.operationalStatus"/></dt><dd><spring:message code="status.${record.status()}"/></dd></div></dl></c:if>
<p><spring:message code="${record.blocked()?'client.deleteBlocked':'client.deleteConfirm'}"/></p>
</div></section>
<c:if test="${not empty actionError}"><div class="fcms-alert" role="alert"><spring:message code="${actionError}"/></div></c:if>
<div class="fcms-form-actions">
<a class="app-btn btn-neutral" href="<c:url value='${clientRoute}/${record.id()}'/>"><spring:message code="common.cancel"/></a>
<c:if test="${record.canDeactivate()}"><button type="submit" name="action" value="deactivate" class="app-btn btn-neutral"><spring:message code="client.markInactive"/></button></c:if>
<c:if test="${not record.blocked()}"><button type="submit" name="action" value="delete" class="app-btn btn-launch"><spring:message code="common.delete"/></button></c:if>
</div></form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
