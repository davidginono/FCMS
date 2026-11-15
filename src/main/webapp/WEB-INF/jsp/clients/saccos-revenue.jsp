<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<c:out value="${category.title}"/>
</h1>
</div>
<c:set var="resourceKey" value="client-category"/>
<c:set var="baseUrl" value="${category.route}"/>
<section class="erp-table-wrap" data-resource-table="client-category" data-table-selection="single" data-table-page-size="${filter.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar">
<h2>
<c:out value="${category.title}"/> <span class="app-table-count">(<c:out value="${totals.totalRows()}"/>)</span></h2>
<div class="app-table-toolbar">
<c:if test="${permissions.contains('client.export')}">
<a class="app-btn btn-neutral" data-table-export="pdf" href="<c:url value='${category.route}/revenues/print.pdf'>
<c:param name='financialYearId' value='${filter.financialYearId}'/>
<c:param name='paymentStatusId' value='${filter.paymentStatusId}'/>
<c:param name='search' value='${filter.search}'/>
<c:param name='reportType' value='${reportType.key}'/>
</c:url>">
<spring:message code="common.pdf" />
</a>
</c:if>
<c:if test="${permissions.contains('client.write')}">
<a class="app-btn btn-launch" href="<c:url value='${category.route}/new'/>">
<spring:message code="common.create" />
</a>
</c:if>
</div>
</div>
<form method="get">
<input type="hidden" name="view" value="revenue">
<label>
<spring:message code="report.type" />
<select name="reportType">
<c:forEach items="${reportTypes}" var="type">
<option value="${type.key}" ${type.key==reportType.key?'selected':''}>
<spring:message code="report.type.${type.key}"/>
</option>
</c:forEach>
</select>
</label>
<input type="hidden" name="sort" value="<c:out value='${filter.sort}'/>">
<input type="hidden" name="direction" value="<c:out value='${filter.direction}'/>">
<input type="hidden" name="size" value="${filter.size}">
<label>
<spring:message code="common.search" />
<input name="search" type="search" maxlength="100" value="<c:out value='${filter.search}'/>">
</label>
<label>
<spring:message code="report.year" />
<select name="financialYearId">
<c:forEach items="${financialYears}" var="year">
<option value="${year.id}" ${filter.financialYearId==year.id?'selected':''}>
<c:out value="${year.fyear}"/>
</option>
</c:forEach>
</select>
</label>
<label>
<spring:message code="common.status" />
<select name="paymentStatusId">
<option value="">
<spring:message code="common.all" />
</option>
<c:forEach items="${paymentStatuses}" var="status">
<option value="${status.id}" ${filter.paymentStatusId==status.id?'selected':''}>
<spring:message code="status.${fn:toUpperCase(status.name)}" text="${status.name}" htmlEscape="true"/>
</option>
</c:forEach>
</select>
</label>
<button class="app-btn btn-neutral" type="submit">
<spring:message code="common.apply" />
</button>
<a class="app-btn btn-neutral" href="<c:url value='${category.route}'/>">
<spring:message code="common.clear" />
</a>
</form>
<div class="erp-table-scroll">
<table class="erp-table">
<thead>
<tr>
<th>
<spring:message code="common.name" />
</th>
<th>
<spring:message code="report.revenue" />
</th>
<th>
<spring:message code="report.profit" />
</th>
<th>
<spring:message code="common.status" />
</th>
<th data-table-actions-header>
<spring:message code="common.actions" />
</th>
</tr>
</thead>
<tbody>
<c:forEach items="${rows}" var="row">
<tr>
<td>
<c:out value="${row.saccosName()}"/>
</td>
<td>
<c:out value="${reportType.rowValue(row)}"/>
</td>
<td>
<c:out value="${row.grossProfit()}"/>
</td>
<td>
<spring:message code="status.${fn:toUpperCase(row.paymentStatus())}" text="${row.paymentStatus()}" htmlEscape="true"/>
</td>
<td>
<a class="app-btn btn-neutral" href="<c:url value='${category.route}/${row.id()}'/>">
<spring:message code="common.view" />
</a>
</td>
</tr>
</c:forEach>
<c:if test="${empty rows}">
<tr>
<td colspan="5">
<spring:message code="common.noRecords" />
</td>
</tr>
</c:if>
</tbody>
</table>
</div>
<div data-table-pagination data-page="${filter.page}" data-table-for="client-category">
<c:if test="${not empty page && page.hasPrevious()}">
<c:url var="pageUrl" value="${category.route}">
<c:param name="search" value="${filter.search}"/>
<c:param name="financialYearId" value="${filter.financialYearId}"/>
<c:param name="paymentStatusId" value="${filter.paymentStatusId}"/>
<c:param name="sort" value="${filter.sort}"/>
<c:param name="direction" value="${filter.direction}"/>
<c:param name="size" value="${filter.size}"/>
<c:param name="view" value="revenue"/>
<c:param name="reportType" value="${reportType.key}"/>
<c:param name="page" value="${filter.page-1}"/>
</c:url>
<a data-page-direction="previous" href="<c:out value='${pageUrl}'/>">previous</a>
</c:if>
<c:if test="${not empty page && page.hasNext()}">
<c:url var="pageUrl" value="${category.route}">
<c:param name="search" value="${filter.search}"/>
<c:param name="financialYearId" value="${filter.financialYearId}"/>
<c:param name="paymentStatusId" value="${filter.paymentStatusId}"/>
<c:param name="sort" value="${filter.sort}"/>
<c:param name="direction" value="${filter.direction}"/>
<c:param name="size" value="${filter.size}"/>
<c:param name="view" value="revenue"/>
<c:param name="reportType" value="${reportType.key}"/>
<c:param name="page" value="${filter.page+1}"/>
</c:url>
<a data-page-direction="next" href="<c:out value='${pageUrl}'/>">next</a>
</c:if>
</div>
</section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
