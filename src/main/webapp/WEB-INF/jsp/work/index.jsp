<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<section class="erp-table-wrap" data-resource-table="${resourceKey}" data-table-selection="single" data-table-wrap data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar">
<h2>
<spring:message code="${kind.title}" />
</h2>
<c:url var="pdfUrl" value="${baseUrl}/print.pdf">
<c:param name="ownerId" value="${query.ownerId}"/>
<c:param name="clientId" value="${query.clientId}"/>
<c:param name="search" value="${query.search}"/>
<c:param name="status" value="${query.status}"/>
<c:param name="from" value="${query.from}"/>
<c:if test="${kind.name()=='TASK'}"><c:param name="dateBasis" value="${query.dateBasis}"/></c:if>
<c:param name="to" value="${query.to}"/>
<c:param name="sort" value="${query.sort}"/>
<c:param name="direction" value="${query.direction}"/>
</c:url>
<a class="app-btn btn-neutral" data-table-export="pdf" href="<c:out value='${pdfUrl}'/>">
<ui:reportExport format="pdf"/>
</a>
<c:if test="${permissions.contains(kind.claim.concat('.write'))}">
<a class="app-btn btn-launch" href="<c:url value='${baseUrl}/new'/>">
<spring:message code="common.create" />
</a>
</c:if>
</div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
<div class="erp-table-scroll">
<table class="erp-table">
<thead>
<tr>
<th data-table-sort-key="subject" data-table-sort-asc="subject" data-table-sort-desc="subject">
<spring:message code="work.subject" />
</th>
<th data-table-sort-key="owner" data-table-sort-asc="owner" data-table-sort-desc="owner">
<spring:message code="common.staff" />
</th>
<th data-table-sort-key="client" data-table-sort-asc="client" data-table-sort-desc="client">
<spring:message code="common.client" />
</th>
<th data-table-sort-key="startAt" data-table-sort-asc="startAt" data-table-sort-desc="startAt">
<spring:message code="operational.start" />
</th>
<th data-table-sort-key="endAt" data-table-sort-asc="endAt" data-table-sort-desc="endAt">
<spring:message code="operational.end" />
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
<a class="resource-table-id-link" href="<c:url value='${baseUrl}/${row.id()}'/>"><c:out value="${row.subject()}"/></a>
</td>
<td>
<c:out value="${row.owner()}" />
</td>
<td>
<c:out value="${row.client()}" />
</td>
<td data-table-date>
<c:out value="${appTime:display(row.startAt())}" />
</td>
<td data-table-date>
<c:out value="${appTime:display(row.endAt())}" />
</td>
<td>
<spring:message code="status.${row.status()}" text="${row.status()}"/>
</td>
<td>
<a class="app-btn btn-neutral" href="<c:url value='${baseUrl}/${row.id()}'/>">
<spring:message code="common.view" />
</a>
</td>
</tr>
</c:forEach>
<c:if test="${empty records.rows()}">
<tr>
<td colspan="7">
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
