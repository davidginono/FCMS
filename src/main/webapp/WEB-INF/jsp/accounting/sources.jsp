<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<section class="erp-table-wrap" data-resource-table="${resourceKey}" data-table-selection="single" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
 <div class="app-table-titlebar"><h2><spring:message code="gl.sources"/></h2></div><%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
 <div class="erp-table-scroll"><table class="erp-table"><thead><tr>
 <th data-table-sort-key="id" data-table-sort-asc="id" data-table-sort-desc="id"><spring:message code="common.reference"/></th><th data-table-sort-key="kind" data-table-sort-asc="kind" data-table-sort-desc="kind"><spring:message code="gl.source.kind"/></th><th data-table-sort-key="date" data-table-sort-asc="date" data-table-sort-desc="date"><spring:message code="common.date"/></th><th data-table-sort-key="reference" data-table-sort-asc="reference" data-table-sort-desc="reference"><spring:message code="gl.source.reference"/></th><th data-table-sort-key="amount" data-table-sort-asc="amount" data-table-sort-desc="amount"><spring:message code="gl.source.amount"/></th><th data-table-sort-key="sourceState" data-table-sort-asc="sourceState" data-table-sort-desc="sourceState"><spring:message code="gl.source.state"/></th><th data-table-sort-key="status" data-table-sort-asc="status" data-table-sort-desc="status"><spring:message code="gl.source.coverage"/></th><th data-table-sort-key="journal" data-table-sort-asc="journal" data-table-sort-desc="journal"><spring:message code="gl.voucherId"/></th><th data-table-actions-header><spring:message code="common.actions"/></th>
 </tr></thead><tbody><c:forEach items="${records.rows()}" var="row"><tr>
 <td><a class="resource-table-id-link" href="<c:url value='/accounting/sources/${row.source().kind()}/${row.source().id()}'/>"><c:out value="${row.source().id()}"/></a></td>
 <td><spring:message code="gl.source.kind.${row.source().kind()}"/></td><td><c:out value="${appTime:date(row.source().date())}"/></td><td><c:out value="${row.source().reference()}"/></td><td><c:out value="${row.source().signedAmount()}"/></td><td><spring:message code="status.${row.source().state()}"/></td><td><spring:message code="status.${row.coverage()}"/></td>
 <td><c:if test="${not empty row.journalId()}"><a class="resource-table-id-link" href="<c:url value='/accounting/journals/${row.journalId()}'/>"><c:out value="${row.journalId()}"/></a></c:if></td>
 <td><a class="app-btn btn-neutral" href="<c:url value='/accounting/sources/${row.source().kind()}/${row.source().id()}'/>"><spring:message code="common.view"/></a></td>
 </tr></c:forEach><c:if test="${empty records.rows()}"><tr><td colspan="9"><spring:message code="common.noRecords"/></td></tr></c:if></tbody></table></div>
 <%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
</section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
