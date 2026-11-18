<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<link rel="stylesheet" href="<c:url value='/css/financial-statements.css?v=20261117-report-workspace2'/>">
<%@ include file="/WEB-INF/jsp/accounting/statement-header.jspf" %>
<%@ include file="/WEB-INF/jsp/accounting/statement-period.jspf" %>
<div class="fs-workspace" data-focused-workspace>
<%@ include file="/WEB-INF/jsp/accounting/statement-tabs.jspf" %>
<div class="fs-toolbar"><%@ include file="/WEB-INF/jsp/accounting/statement-view-settings.jspf" %></div>
<div data-fs-report-section>
<c:choose><c:when test="${empty statement}"><div class="fcms-alert" data-message-type="warning" role="status"><spring:message code="gl.error.opening"/></div></c:when><c:otherwise>
<%@ include file="/WEB-INF/jsp/accounting/statement-paper.jspf" %>
<section class="erp-resource-section"><h2><spring:message code="gl.reportStatus"/></h2><div class="fcms-section-body"><dl class="fcms-details"><div><dt><spring:message code="gl.policy"/></dt><dd><c:out value="${statement.policy()}"/></dd></div><div><dt><spring:message code="gl.unposted"/></dt><dd><c:out value="${statement.unposted()}"/></dd></div><c:if test="${reportType=='income-statement' or reportType=='balance-sheet' or reportType=='notes'}"><div><dt><spring:message code="gl.reporting.unclassified"/></dt><dd><c:out value="${comparison.unclassifiedCount()}"/></dd></div><div><dt><spring:message code="gl.reporting.revisionCount"/></dt><dd><c:out value="${comparison.reportingVersionIds().size()}"/></dd></div></c:if><div><dt><spring:message code="gl.reconciliation"/></dt><dd><spring:message code="${statement.balanceSheetMatches() and statement.cashFlowMatches() and statement.equityMatches()?'gl.reconciled':'gl.errorReconciliation'}"/></dd></div></dl></div></section>
<c:if test="${reportType=='changes-in-equity' and not empty comparison.equityMatrix().checks()}"><section class="erp-resource-section"><h2><spring:message code="gl.equity.checks"/></h2><div class="fcms-section-body"><ul><c:forEach items="${comparison.equityMatrix().checks()}" var="check"><li><spring:message code="gl.equity.check.${check}" text="${check}"/></li></c:forEach></ul></div></section></c:if><c:set var="sourceCoverage" value="${statement.sourceCoverage()}"/><%@ include file="/WEB-INF/jsp/accounting/source-coverage.jspf" %>
<c:if test="${comparison.previousAvailable()}">
<section class="erp-resource-section"><h2><spring:message code="gl.presentation.previous"/></h2><div class="fcms-section-body"><dl class="fcms-details">
<div><dt><spring:message code="gl.presentation.period"/></dt><dd><c:out value="${appTime:date(comparison.previousFrom())}"/> — <c:out value="${appTime:date(comparison.previousTo())}"/></dd></div>
<div><dt><spring:message code="gl.policy"/></dt><dd><c:out value="${comparison.previous().policy()}"/></dd></div>
<div><dt><spring:message code="gl.unposted"/></dt><dd><c:out value="${comparison.previous().unposted()}"/></dd></div><div><dt><spring:message code="gl.reconciliation"/></dt><dd><spring:message code="${comparison.previous().balanceSheetMatches() and comparison.previous().cashFlowMatches() and comparison.previous().equityMatches()?'gl.reconciled':'gl.errorReconciliation'}"/></dd></div>
</dl></div></section><c:set var="sourceCoverage" value="${comparison.previous().sourceCoverage()}"/><%@ include file="/WEB-INF/jsp/accounting/source-coverage.jspf" %>
</c:if>
</c:otherwise></c:choose></div></div><script defer src="<c:url value='/js/financial-statement-view.js?v=20261117-report-workspace2'/>"></script>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
