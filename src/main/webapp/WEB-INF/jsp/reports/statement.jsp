<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/financial-statements.css?v=20261113-headers1'/>">
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="report.statement"/></h1></div>
<form class="app-actions" method="get">
<label for="year"><spring:message code="report.year"/></label>
<input id="year" name="year" type="number" min="2000" max="2100" value="${statement.year()}">
<button class="app-btn btn-neutral" type="submit"><spring:message code="common.apply"/></button>
<c:url var="pdfUrl" value="/reports/statement.pdf"><c:param name="year" value="${statement.year()}"/></c:url>
<c:url var="printUrl" value="/reports/print"><c:param name="year" value="${statement.year()}"/></c:url>
<a class="app-btn btn-neutral" href="<c:out value='${pdfUrl}'/>"><spring:message code="common.pdf"/></a>
<a class="app-btn btn-neutral" href="<c:out value='${printUrl}'/>"><spring:message code="common.print"/></a>
</form>
<div class="fs-workspace" data-focused-workspace>
<%@ include file="/WEB-INF/jsp/accounting/statement-paper.jspf" %>
<details class="erp-resource-section fs-checks"><summary><spring:message code="report.clients"/></summary>
<div class="fcms-section-body"><ul><c:forEach items="${statementDocument.provenance()}" var="value"><li><c:out value="${value}"/></li></c:forEach></ul></div>
</details></div>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
