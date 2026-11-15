<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="nav.backups" />
</h1>
</div>
<section class="erp-table-wrap" data-resource-table="backups" data-table-selection="single" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar">
<h2>
<spring:message code="nav.backups" />
</h2>
<div class="app-table-toolbar"><form method="post" action="<c:url value='/backups'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<button class="app-btn btn-launch" type="submit">
<spring:message code="backup.create" />
</button>
</form></div>
</div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
<div class="erp-table-scroll">
<table class="erp-table">
<thead>
<tr>
<th data-table-sort-key="id" data-table-sort-asc="id" data-table-sort-desc="id">
<spring:message code="common.reference" />
</th>
<th data-table-sort-key="status" data-table-sort-asc="status" data-table-sort-desc="status">
<spring:message code="common.status" />
</th>
<th data-table-sort-key="date" data-table-sort-asc="date" data-table-sort-desc="date">
<spring:message code="backup.created" />
</th>
<th data-table-sort-key="size" data-table-sort-asc="size" data-table-sort-desc="size">
<spring:message code="backup.size" />
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
<c:out value="${row.id()}"/>
</td>
<td>
<spring:message code="status.${row.status()}" text="${row.status()}"/>
</td>
<td>
<c:out value="${appTime:display(row.createdAt())}"/>
</td>
<td>
<c:out value="${row.size()}"/>
</td>
<td>
<c:if test="${row.status()=='READY'}">
<a class="app-btn btn-neutral" href="<c:url value='/backups/${row.id()}/download'/>">
<spring:message code="common.download" />
</a>
</c:if>
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
