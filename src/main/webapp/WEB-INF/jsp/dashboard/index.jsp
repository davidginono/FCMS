<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="nav.dashboard" />
</h1>
</div>
<section class="fcms-kpis">
<c:forEach items="${metrics}" var="metric">
<article>
<span>
<spring:message code="${metric.label()}"/>
</span>
<strong>
<c:out value="${metric.value()}"/>
</strong>
</article>
</c:forEach>
</section>
<section class="fcms-kpis">
<c:forEach items="${portfolios}" var="portfolio">
<article>
<h2>
<spring:message code="client.category.${portfolio.category()}"/>
</h2>
<strong>
<c:out value="${portfolio.clients()}"/>
</strong>
<dl>
<dt>
<spring:message code="report.paid" />
</dt>
<dd>
<c:out value="${portfolio.paid()}"/>
</dd>
<dt>
<spring:message code="report.unpaid" />
</dt>
<dd>
<c:out value="${portfolio.unpaid()}"/>
</dd>
</dl>
</article>
</c:forEach>
</section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
