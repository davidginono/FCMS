<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<section class="erp-table-wrap" data-resource-table="expense-categories" data-table-sync-query="true" data-table-wrap data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar">
<h2><spring:message code="expense.categories"/></h2>
<c:url var="pdfUrl" value="/finance/expenses/categories/print.pdf">
<c:param name="year" value="${query.year}"/><c:param name="category" value="${query.category}"/>
<c:param name="ownerId" value="${query.ownerId}"/><c:param name="search" value="${query.search}"/>
<c:param name="status" value="${query.status}"/><c:param name="from" value="${query.from}"/>
<c:param name="to" value="${query.to}"/><c:param name="sort" value="${query.sort}"/>
<c:param name="direction" value="${query.direction}"/>
</c:url>
<a class="app-btn btn-neutral" data-table-export="pdf" href="<c:out value='${pdfUrl}'/>"><spring:message code="common.pdf"/></a>
</div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
<div data-table-query-state-for="expense-categories" class="resource-table-summary"><spring:message code="common.total"/> (TZS): <span data-table-query-value="total"><fmt:formatNumber value="${total}" minFractionDigits="2" maxFractionDigits="2"/></span></div>
<div class="erp-table-scroll">
<table class="erp-table">
<thead><tr>
<th data-table-sort-key="category" data-table-sort-asc="category" data-table-sort-desc="category"><spring:message code="ledger.category"/></th>
<th data-table-sort-key="amount" data-table-sort-asc="amount" data-table-sort-desc="amount"><spring:message code="common.amount"/></th>
</tr></thead>
<tbody>
<c:forEach items="${records.rows()}" var="row">
<c:url var="detailsUrl" value="/finance/expenses">
<c:param name="category" value="${row.category()}"/><c:param name="year" value="${query.year}"/>
<c:param name="ownerId" value="${query.ownerId}"/><c:param name="status" value="${query.status}"/>
<c:param name="from" value="${query.from}"/><c:param name="to" value="${query.to}"/>
</c:url>
<tr><td><a class="resource-table-id-link" href="<c:out value='${detailsUrl}'/>"><c:out value="${row.category()}"/></a></td>
<td data-table-number><fmt:formatNumber value="${row.amount()}" minFractionDigits="2" maxFractionDigits="2"/></td></tr>
</c:forEach>
<c:if test="${empty records.rows()}"><tr><td colspan="2"><spring:message code="common.noRecords"/></td></tr></c:if>
</tbody>
</table>
</div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
</section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
