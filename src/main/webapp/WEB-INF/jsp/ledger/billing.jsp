<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<section class="erp-table-wrap" data-resource-table="cloud-billing" data-table-selection="single" data-table-wrap data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar">
<h2><spring:message code="cloud.billing"/></h2>
<c:url var="billingPdfUrl" value="/finance/cloud-backup/billing/print.pdf">
<c:param name="year" value="${year}"/><c:param name="search" value="${query.search}"/>
<c:param name="status" value="${query.status}"/><c:param name="sort" value="${query.sort}"/>
<c:param name="direction" value="${query.direction}"/>
</c:url>
<a class="app-btn btn-neutral" data-table-export="pdf" href="<c:out value='${billingPdfUrl}'/>"><spring:message code="common.pdf"/></a>
</div>
<form method="get">
<input type="hidden" name="size" value="${query.size}">
<input type="hidden" name="sort" value="<c:out value='${query.sort}'/>">
<input type="hidden" name="direction" value="${query.direction}">
<label>
<spring:message code="report.year" />
<input name="year" type="number" min="2000" max="2100" value="${year}">
</label>
<label>
<spring:message code="common.search" />
<input name="search" type="search" maxlength="100" value="<c:out value='${query.search}'/>">
</label>
<label>
<spring:message code="common.status" />
<select name="status">
<option value="">
<spring:message code="common.all" />
</option>
<option value="PAID" ${query.status=='PAID'?'selected':''}>
<spring:message code="report.paid" />
</option>
<option value="UNPAID" ${query.status=='UNPAID'?'selected':''}>
<spring:message code="report.unpaid" />
</option>
</select>
</label>
<button class="app-btn btn-neutral" type="submit">
<spring:message code="common.apply" />
</button>
</form>
<div class="erp-table-scroll">
<table class="erp-table">
<thead>
<tr>
<th data-table-sort-key="name" data-table-sort-asc="name" data-table-sort-desc="name">
<spring:message code="common.name" />
</th>
<th data-table-sort-key="fee" data-table-sort-asc="fee" data-table-sort-desc="fee">
<spring:message code="cloud.fee" />
</th>
<th data-table-sort-key="cost" data-table-sort-asc="cost" data-table-sort-desc="cost">
<spring:message code="cloud.cost" />
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
<a class="resource-table-id-link" href="<c:url value='/clients/saccos/${row.id()}'/>"><c:out value="${row.name()}"/></a>
</td>
<td data-table-number>
<c:out value="${row.fee()}"/>
</td>
<td data-table-number>
<c:out value="${row.cost()}"/>
</td>
<td>
<spring:message code="status.${row.status()}" text="${row.status()}"/>
</td>
<td>
<a href="<c:url value='/clients/saccos/${row.id()}'/>" class="app-btn btn-neutral">
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
