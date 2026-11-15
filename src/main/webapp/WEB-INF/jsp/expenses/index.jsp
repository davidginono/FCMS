<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<section class="erp-table-wrap" data-resource-table="expenses" data-table-selection="single" data-table-wrap data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar">
<h2>
<spring:message code="nav.expenses" />
</h2>
<c:url var="pdfUrl" value="${baseUrl}/print.pdf">
<c:param name="year" value="${query.year}"/><c:param name="category" value="${query.category}"/>
<c:param name="ownerId" value="${query.ownerId}"/>
<c:param name="clientId" value="${query.clientId}"/>
<c:param name="search" value="${query.search}"/>
<c:param name="status" value="${query.status}"/>
<c:param name="from" value="${query.from}"/>
<c:param name="to" value="${query.to}"/>
<c:param name="sort" value="${query.sort}"/>
<c:param name="direction" value="${query.direction}"/>
</c:url>
<a class="app-btn btn-neutral" data-table-export="pdf" href="<c:out value='${pdfUrl}'/>">
<spring:message code="common.pdf" />
</a>
<a class="app-btn btn-neutral" href="<c:url value='/finance/expenses/categories'/>"><spring:message code="expense.categories"/></a>
<c:if test="${permissions.contains('expense.write')}">
<a class="app-btn btn-launch" href="<c:url value='/finance/expenses/new'/>">
<spring:message code="common.create" />
</a>
</c:if>
</div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
<div class="erp-table-scroll">
<table class="erp-table">
<thead>
<tr>
<th data-table-sort-key="date" data-table-sort-asc="date" data-table-sort-desc="date">
<spring:message code="common.date" />
</th>
<th data-table-sort-key="owner" data-table-sort-asc="owner" data-table-sort-desc="owner">
<spring:message code="common.staff" />
</th>
<th data-table-sort-key="category" data-table-sort-asc="category" data-table-sort-desc="category">
<spring:message code="ledger.category" />
</th>
<th data-table-sort-key="description" data-table-sort-asc="description" data-table-sort-desc="description">
<spring:message code="common.description" />
</th>
<th data-table-sort-key="payee" data-table-sort-asc="payee" data-table-sort-desc="payee">
<spring:message code="expense.payee" />
</th>
<th data-table-sort-key="amount" data-table-sort-asc="amount" data-table-sort-desc="amount">
<spring:message code="common.amount" />
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
<td data-table-date>
<c:out value="${appTime:display(row.date())}" />
</td>
<td>
<c:out value="${row.owner()}" />
</td>
<td>
<c:out value="${row.category()}" />
</td>
<td>
<a class="resource-table-id-link" href="<c:url value='/finance/expenses/${row.id()}'/>"><c:out value="${row.description()}" /></a>
</td>
<td>
<c:out value="${row.payee()}" />
</td>
<td data-table-number>
<c:out value="${row.amount()}" />
</td>
<td>
<spring:message code="status.${row.status()}" text="${row.status()}"/>
</td>
<td>
<a class="app-btn btn-neutral" href="<c:url value='/finance/expenses/${row.id()}'/>">
<spring:message code="common.view" />
</a>
</td>
</tr>
</c:forEach>
<c:if test="${empty records.rows()}">
<tr>
<td colspan="8">
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
