<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><spring:htmlEscape defaultHtmlEscape="true"/>
<!DOCTYPE html><html lang="${pageContext.response.locale.language}"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title><spring:message code="${reportTitle}"/></title><link rel="stylesheet" href="<c:url value='/css/financial-statement-print.css?v=20261113-headers1'/>"><link rel="stylesheet" href="<c:url value='/css/print-footer.css?v=20261112'/>"><c:if test="${empty statement}"><link rel="stylesheet" href="<c:url value='/css/notifications.css?v=20261117-cloudscape-notices1'/>"></c:if></head><body class="financial-statement-print" data-print-document>
<c:choose><c:when test="${empty statement}"><div class="fcms-alert" data-message-type="warning" role="status"><spring:message code="gl.error.opening"/></div></c:when><c:otherwise>
<section class="fs-print-document fs-print-${statementDocument.style()} ${statementDocument.landscape()?'fs-print-landscape':'fs-print-portrait'}">
<c:choose><c:when test="${statementDocument.style()=='equity'}">
<h1><c:out value="${statementDocument.title()}"/></h1>
<p class="fs-equity-period"><span><spring:message code="gl.equity.forPeriod"/></span><span><c:out value="${statementDocument.header().period().substring(statementDocument.header().period().lastIndexOf('       ')+7)}"/></span></p>
</c:when><c:otherwise>
<h1><c:out value="${statementDocument.title()}"/></h1>
<header><c:forEach items="${statementDocument.context()}" var="value"><p><c:out value="${value}"/></p></c:forEach></header>
</c:otherwise></c:choose>
<table data-table-sort-mode="none" data-table-sort-reason="document">
<colgroup><c:forEach items="${statementDocument.columns()}" var="column"><col width="${column.weight()*100}%"></c:forEach></colgroup>
<thead><tr><c:forEach items="${statementDocument.columns()}" var="column"><th scope="col" class="${column.numeric()?'fs-print-number':''}"><c:out value="${column.label()}"/></th></c:forEach></tr></thead>
<tbody><c:forEach items="${statementDocument.rows()}" var="row"><tr class="${row.heading()?'fs-print-section':''} ${row.total()?'fs-print-total':''}">
<c:choose><c:when test="${row.heading()}"><td colspan="${statementDocument.columns().size()}"><c:out value="${row.label()}"/></td></c:when><c:otherwise>
<c:forEach items="${row.cells()}" var="cell" varStatus="cs"><td class="${statementDocument.columns()[cs.index].numeric() and row.numericCell(cs.index)?'fs-print-number':''}"><c:out value="${cell}"/></td></c:forEach>
</c:otherwise></c:choose></tr></c:forEach></tbody></table><c:if test="${statementDocument.style()=='equity'}"><p class="fs-equity-scope"><c:out value="${statementDocument.context()[1]}"/></p></c:if>
<footer><p><c:out value="${statementDocument.notice()}"/></p></footer></section>
<c:if test="${not empty statementDocument.provenance()}"><section class="fs-print-document ${statementDocument.landscape()?'fs-print-landscape':'fs-print-portrait'}"><h1><spring:message code="gl.reportStatus"/></h1><ul><c:forEach items="${statementDocument.provenance()}" var="value"><li><c:out value="${value}"/></li></c:forEach></ul></section></c:if>
</c:otherwise></c:choose></body></html>
