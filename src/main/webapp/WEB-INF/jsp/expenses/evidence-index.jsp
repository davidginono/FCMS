<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<section class="erp-table-wrap" data-resource-table="${resourceKey}" data-table-selection="single" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100"><div class="app-table-titlebar"><h2><spring:message code="expense.evidence.title"/></h2></div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
<div class="erp-table-scroll"><table class="erp-table"><thead><tr>
<th data-table-sort-key="id" data-table-sort-asc="id" data-table-sort-desc="id">ID</th>
<th data-table-sort-key="date" data-table-sort-asc="date" data-table-sort-desc="date"><spring:message code="expense.evidence.expenseDate"/></th>
<th data-table-sort-key="owner" data-table-sort-asc="owner" data-table-sort-desc="owner"><spring:message code="common.staff"/></th>
<th data-table-sort-key="payee" data-table-sort-asc="payee" data-table-sort-desc="payee"><spring:message code="expense.payee"/></th>
<th data-table-sort-key="amount" data-table-sort-asc="amount" data-table-sort-desc="amount"><spring:message code="common.amount"/></th>
<th data-table-sort-key="expenseStatus" data-table-sort-asc="expenseStatus" data-table-sort-desc="expenseStatus"><spring:message code="common.status"/></th>
<th data-table-sort-key="status" data-table-sort-asc="status" data-table-sort-desc="status"><spring:message code="expense.evidence.status"/></th>
<th data-table-actions-header><spring:message code="common.actions"/></th></tr></thead><tbody>
<c:forEach items="${records.rows()}" var="row"><tr><td><a class="resource-table-id-link" href="<c:url value='/finance/expenses/${row.id()}/evidence'/>">#<c:out value="${row.id()}"/></a></td><td><c:out value="${appTime:date(row.expenseDate())}"/></td><td><c:out value="${row.owner()}"/></td><td><c:out value="${row.payee()}"/></td><td><fmt:formatNumber value="${row.amount()}" minFractionDigits="2" maxFractionDigits="2"/></td><td><spring:message code="status.${row.expenseStatus()}"/></td><td><spring:message code="status.${row.evidenceState()}"/></td><td><a class="app-btn btn-neutral" href="<c:url value='/finance/expenses/${row.id()}/evidence'/>"><spring:message code="common.details"/></a></td></tr></c:forEach>
<c:if test="${empty records.rows()}"><tr><td colspan="8"><spring:message code="common.noRecords"/></td></tr></c:if></tbody></table></div><%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %></section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
