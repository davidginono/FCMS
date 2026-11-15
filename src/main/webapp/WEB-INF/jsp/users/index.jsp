<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="nav.users" />
</h1>
</div>
<c:set var="baseUrl" value="/admin/users"/>
<c:set var="resourceKey" value="users"/>
<section class="erp-table-wrap" data-resource-table="users" data-table-selection="single" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar">
<h2>
<spring:message code="nav.users" />
</h2>
<c:if test="${permissions.contains('iam.user.manage')}">
<a class="app-btn btn-launch" href="<c:url value='/admin/users/new'/>">
<spring:message code="common.create" />
</a>
</c:if>
</div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
<div class="erp-table-scroll">
<table class="erp-table">
<thead>
<tr>
<th data-table-sort-key="name" data-table-sort-asc="name" data-table-sort-desc="name">
<spring:message code="common.name" />
</th>
<th data-table-sort-key="email" data-table-sort-asc="email" data-table-sort-desc="email">
<spring:message code="common.email" />
</th>
<th data-table-sort-key="role" data-table-sort-asc="role" data-table-sort-desc="role">
<spring:message code="common.role" />
</th>
<th data-table-sort-key="status" data-table-sort-asc="status" data-table-sort-desc="status">
<spring:message code="common.status" />
</th>
<th data-table-actions-header>
<spring:message code="common.actions" />
</th>
</tr>
</thead>
<tbody>
<c:forEach items="${records.rows()}" var="row">
<tr>
<td>
<a class="resource-table-id-link" href="<c:url value='/admin/users/${row.id()}'/>"><c:out value="${row.name()}"/></a>
</td>
<td>
<c:out value="${row.email()}" />
</td>
<td>
<c:out value="${row.roleName()}" />
</td>
<td>
<spring:message code="status.${row.status()}" text="${row.status()}"/>
</td>
<td>
<a class="app-btn btn-neutral" href="<c:url value='/admin/users/${row.id()}' />">
<spring:message code="common.view" />
</a>
</td>
</tr>
</c:forEach>
<c:if test="${empty records.rows()}">
<tr>
<td colspan="5">
<spring:message code="common.noRecords" />
</td>
</tr>
</c:if>
</tbody>
</table>
</div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
</section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
