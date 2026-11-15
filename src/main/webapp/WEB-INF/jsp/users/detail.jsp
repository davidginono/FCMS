<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="iam.user.details" />
</h1>
</div>
<div class="app-actions">
<c:if test="${permissions.contains('iam.user.manage')}">
<a class="app-btn btn-neutral" href="<c:url value='/admin/users/${record.id()}/edit'/>">
<spring:message code="common.edit" />
</a>
</c:if>
</div>
<section class="erp-resource-section"><h2><spring:message code="common.details"/></h2><div class="fcms-section-body">
<dl class="fcms-details">
<div>
<dt>
<spring:message code="common.name" />
</dt>
<dd>
<c:out value="${record.name()}" />
</dd>
</div>
<div>
<dt>
<spring:message code="common.email" />
</dt>
<dd>
<c:out value="${record.email()}" />
</dd>
</div>
<div>
<dt>
<spring:message code="common.role" />
</dt>
<dd>
<c:out value="${record.roleName()}" />
</dd>
</div>
<div>
<dt>
<spring:message code="common.status" />
</dt>
<dd>
<spring:message code="status.${record.status()}" text="${record.status()}"/>
</dd>
</div>
</dl>
</div></section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
