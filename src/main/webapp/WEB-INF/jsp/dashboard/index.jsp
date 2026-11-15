<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="nav.dashboard"/></h1></div>
<form method="get" action="<c:url value='/dashboard'/>" class="dashboard-year-form">
<label for="dashboardYear"><spring:message code="report.year"/></label>
<select id="dashboardYear" name="financialYearId" ${empty overview.years()?'disabled':''}>
<c:if test="${empty overview.years()}"><option><spring:message code="gl.presentation.unavailable"/></option></c:if>
<c:forEach items="${overview.years()}" var="year"><option value="${year.id()}" ${year.id()==overview.selected().id()?'selected':''}><c:out value="${year.label()}"/></option></c:forEach>
</select><button type="submit" class="app-btn btn-neutral" ${empty overview.years()?'disabled':''}><spring:message code="common.apply"/></button>
</form>
<div class="dashboard-grid">
<c:forEach items="${overview.work()}" var="metric"><section class="dashboard-widget" data-dashboard-work="${metric.label()}"><h2><spring:message code="${metric.label()}"/></h2><div class="dashboard-widget-body"><a class="dashboard-value" href="<c:url value='${metric.href()}'/>"><c:out value="${metric.value()}"/></a></div></section></c:forEach>
<c:forEach items="${overview.money()}" var="metric"><section class="dashboard-widget" data-dashboard-money="${metric.key()}"><h2><spring:message code="${metric.label()}"/></h2><div class="dashboard-widget-body"><c:choose><c:when test="${metric.value()!=null}"><a class="dashboard-value" href="<c:url value='${metric.href()}'/>"><fmt:formatNumber value="${metric.value()}" minFractionDigits="2" maxFractionDigits="2"/></a></c:when><c:otherwise><span class="dashboard-value"><spring:message code="gl.presentation.unavailable"/></span></c:otherwise></c:choose></div></section></c:forEach>
</div>
<div class="dashboard-grid dashboard-grid--clients">
<c:forEach items="${overview.portfolios()}" var="portfolio">
<c:url var="clientUrl" value="/clients/${portfolio.slug()}"><c:param name="financialYearId" value="${overview.selected().id()}"/></c:url>
<c:url var="revenueUrl" value="/clients/${portfolio.slug()}"><c:param name="financialYearId" value="${overview.selected().id()}"/><c:param name="view" value="revenue"/></c:url>
<section class="dashboard-widget" data-dashboard-client="${portfolio.slug()}"><h2><spring:message code="client.category.${portfolio.category()}"/></h2><div class="dashboard-widget-body">
<a class="dashboard-value" href="<c:out value='${clientUrl}'/>"><c:out value="${portfolio.clients()}"/></a>
<dl class="dashboard-facts">
<c:forEach items="${['1','2','3']}" var="state"><c:set var="statusLabel" value="${state=='1'?'status.PAID':state=='2'?'status.UNPAID':'status.PENDING'}"/><c:url var="statusUrl" value="/clients/${portfolio.slug()}"><c:param name="financialYearId" value="${overview.selected().id()}"/><c:param name="paymentStatusId" value="${state}"/></c:url>
<div><dt><spring:message code="${statusLabel}"/></dt><dd><a href="<c:out value='${statusUrl}'/>"><c:out value="${state=='1'?portfolio.paid():state=='2'?portfolio.unpaid():portfolio.pending()}"/></a></dd></div></c:forEach>
<div><dt><spring:message code="client.annualFee"/></dt><dd><a href="<c:out value='${revenueUrl}'/>"><fmt:formatNumber value="${portfolio.annualFee()}" minFractionDigits="2" maxFractionDigits="2"/></a></dd></div>
<div><dt><spring:message code="client.amountPaid"/></dt><dd><fmt:formatNumber value="${portfolio.received()}" minFractionDigits="2" maxFractionDigits="2"/></dd></div>
<c:if test="${portfolio.category()=='saccos'}"><div><dt><spring:message code="client.withholdingTax"/></dt><dd><fmt:formatNumber value="${portfolio.withholdingTax()}" minFractionDigits="2" maxFractionDigits="2"/></dd></div></c:if>
<div><dt><spring:message code="${portfolio.category()=='saccos'?'dashboard.annualAfterTax':'dashboard.annualDifference'}"/></dt><dd><a href="<c:out value='${revenueUrl}'/>"><fmt:formatNumber value="${portfolio.annualDifference()}" minFractionDigits="2" maxFractionDigits="2"/></a></dd></div>
<c:forEach items="${portfolio.fees()}" var="fee"><c:url var="feeUrl" value="/clients/${portfolio.slug()}"><c:param name="financialYearId" value="${overview.selected().id()}"/><c:param name="view" value="revenue"/><c:param name="reportType" value="${fee.reportType()}"/></c:url><div><dt><spring:message code="${fee.label()}"/></dt><dd><a href="<c:out value='${feeUrl}'/>"><fmt:formatNumber value="${fee.amount()}" minFractionDigits="2" maxFractionDigits="2"/></a></dd></div></c:forEach>
</dl></div></section>
<c:if test="${overview.cloudBilling() && portfolio.category()=='saccos'}">
<c:url var="cloudUrl" value="/finance/cloud-backup/billing"><c:param name="year" value="${overview.selected().calendarYear()}"/></c:url>
<section class="dashboard-widget" data-dashboard-cloud><h2><spring:message code="cloud.billing"/></h2><div class="dashboard-widget-body"><a class="dashboard-value" href="<c:out value='${cloudUrl}'/>"><c:out value="${portfolio.clients()}"/></a><dl class="dashboard-facts">
<c:forEach items="${['PAID','UNPAID']}" var="state"><c:url var="cloudStatusUrl" value="/finance/cloud-backup/billing"><c:param name="year" value="${overview.selected().calendarYear()}"/><c:param name="status" value="${state}"/></c:url><div><dt><spring:message code="status.${state}"/></dt><dd><a href="<c:out value='${cloudStatusUrl}'/>"><c:out value="${state=='PAID'?portfolio.cloudPaid():portfolio.cloudUnpaid()}"/></a></dd></div></c:forEach>
<div><dt><spring:message code="cloud.fee"/></dt><dd><fmt:formatNumber value="${portfolio.cloudFee()}" minFractionDigits="2" maxFractionDigits="2"/></dd></div><div><dt><spring:message code="cloud.cost"/></dt><dd><fmt:formatNumber value="${portfolio.cloudCost()}" minFractionDigits="2" maxFractionDigits="2"/></dd></div>
</dl></div></section></c:if>
</c:forEach>
</div>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
