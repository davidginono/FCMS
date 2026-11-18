<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/financial-statements.css?v=20261117-report-workspace2'/>">
<nav class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><ol class="erp-page-path__list"><li><a href="<c:url value='/accounting/statements'/>"><spring:message code="gl.statements"/></a></li><li><span class="erp-page-path__separator" aria-hidden="true">/</span><span aria-current="page"><spring:message code="gl.preview"/></span></li></ol></nav>
<%@ include file="/WEB-INF/jsp/accounting/statement-header.jspf" %>
<%@ include file="/WEB-INF/jsp/accounting/statement-period.jspf" %>
<div class="fs-workspace" data-focused-workspace>
<%@ include file="/WEB-INF/jsp/accounting/statement-tabs.jspf" %>
<div class="fs-toolbar"><%@ include file="/WEB-INF/jsp/accounting/statement-view-settings.jspf" %></div>
<div data-fs-report-section>
<c:choose><c:when test="${empty statement}">
<div class="fcms-alert" data-message-type="warning" role="status"><spring:message code="gl.error.opening"/></div>
</c:when><c:otherwise>
<section class="erp-table-wrap fs-workspace" data-resource-table="gl-trial" data-table-presentation="statement" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar"><h2><spring:message code="gl.trialBalance"/> <span class="app-table-count">(${trialTotals.count()})</span></h2></div>
<%@ include file="/WEB-INF/jsp/accounting/statement-filters.jspf" %>
<%@ include file="/WEB-INF/jsp/accounting/statement-paper.jspf" %>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
</section>
<c:set var="sourceCoverage" value="${statement.sourceCoverage()}"/><%@ include file="/WEB-INF/jsp/accounting/source-coverage.jspf" %>
</c:otherwise></c:choose>
</div></div>
<script defer src="<c:url value='/js/financial-statement-view.js?v=20261117-report-workspace2'/>"></script>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
