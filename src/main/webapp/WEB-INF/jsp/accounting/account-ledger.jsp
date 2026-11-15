<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/financial-statements.css?v=20261113-headers1'/>">
<c:choose><c:when test="${empty statement}">
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="gl.generalLedger"/></h1></div><div class="fcms-alert" role="status"><spring:message code="gl.error.opening"/></div>
</c:when><c:otherwise>
<section class="erp-table-wrap fs-workspace" data-resource-table="gl-account-ledger" data-table-presentation="statement" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar"><h2><spring:message code="gl.generalLedger"/> <span class="app-table-count">(${ledger.matchingCount()})</span></h2>
<form method="get" action="<c:url value='${baseUrl}/print.pdf'/>" class="app-table-toolbar" data-table-export-for="gl-account-ledger">
<input type="hidden" name="from" value="${query.from}"><input type="hidden" name="to" value="${query.to}"><input type="hidden" name="search" value="<c:out value='${query.search}'/>"><input type="hidden" name="sort" value="<c:out value='${query.sort}'/>"><input type="hidden" name="direction" value="${query.direction}">
<button type="submit" class="app-btn btn-neutral" data-download-action="true"><spring:message code="common.pdf"/></button><button type="submit" class="app-btn btn-neutral" formaction="<c:url value='${baseUrl}/print'/>" data-print-action="true"><spring:message code="common.print"/></button>
</form></div>
<%@ include file="/WEB-INF/jsp/accounting/statement-filters.jspf" %>
<div class="fs-toolbar"><%@ include file="/WEB-INF/jsp/accounting/statement-view-settings.jspf" %></div>
<%@ include file="/WEB-INF/jsp/accounting/statement-paper.jspf" %>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
</section>
<c:set var="sourceCoverage" value="${ledger.sourceCoverage()}"/><%@ include file="/WEB-INF/jsp/accounting/source-coverage.jspf" %>
</c:otherwise></c:choose>
<script defer src="<c:url value='/js/financial-statement-view.js?v=20261103'/>"></script>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
