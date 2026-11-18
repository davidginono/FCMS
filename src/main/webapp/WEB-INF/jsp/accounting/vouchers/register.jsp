<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-entry.css?v=20261118-split2'/>">
<section data-voucher-kind="${voucherKind}" class="erp-table-wrap" data-resource-table="${resourceKey}" data-table-selection="single" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar"><h2><spring:message code="voucher.register.${voucherKind}"/></h2><c:if test="${permissions.contains('accounting.write') and not legacyRegister}"><a class="app-btn btn-launch" href="<c:url value='${baseUrl}/new'/>"><spring:message code="common.create"/></a></c:if></div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
<div class="erp-table-scroll"><table class="erp-table"><thead><tr><th data-table-sort-key="date"><spring:message code="common.date"/></th><th data-table-sort-key="reference"><spring:message code="common.reference"/></th><th data-table-sort-key="amount"><spring:message code="common.amount"/></th><th data-table-sort-key="maker"><spring:message code="gl.maker"/></th><th data-table-sort-key="status"><spring:message code="common.status"/></th><th data-table-actions-header><spring:message code="common.actions"/></th></tr></thead><tbody>
<c:forEach items="${records.rows()}" var="row">
<c:url var="viewVoucherUrl" value="${baseUrl}/${row.id()}"/>
<c:choose><c:when test="${legacyRegister}"><c:url var="pdfVoucherUrl" value="/accounting/journals/${row.id()}/print.pdf"/><c:set var="printVoucherUrl" value="${pdfVoucherUrl}"/></c:when><c:otherwise><c:url var="pdfVoucherUrl" value="${baseUrl}/${row.id()}/pdf"/><c:url var="printVoucherUrl" value="${baseUrl}/${row.id()}/print"/></c:otherwise></c:choose>
<tr><td data-table-date><c:out value="${appTime:date(row.date())}"/></td><td><a class="resource-table-id-link" href="${viewVoucherUrl}"><c:out value="${row.reference()}"/></a></td><td data-table-amount><fmt:formatNumber value="${row.total()}" minFractionDigits="2" maxFractionDigits="2"/></td><td><c:out value="${row.maker()}"/></td><td><spring:message code="status.${row.state()}"/></td><td><a class="app-btn btn-neutral" href="${viewVoucherUrl}"><spring:message code="common.view"/></a><c:if test="${permissions.contains('accounting.report')}"><a class="app-btn btn-neutral" target="_blank" rel="noopener" href="${pdfVoucherUrl}"><ui:reportExport format="pdf"/></a><a class="app-btn btn-neutral" target="_blank" rel="noopener" href="${printVoucherUrl}"><ui:reportExport format="print"/></a></c:if></td></tr>
</c:forEach>
<c:if test="${empty records.rows()}"><tr><td colspan="6"><span class="resource-table-empty-message"><spring:message code="common.noRecords"/></span></td></tr></c:if>
</tbody></table></div><%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
</section><%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
