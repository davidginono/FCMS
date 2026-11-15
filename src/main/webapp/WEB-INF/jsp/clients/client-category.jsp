<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<c:set var="resourceKey" value="client-${category.slug}"/>
<c:set var="baseUrl" value="${category.route}"/>
<section class="erp-table-wrap" data-resource-table="client-${category.slug}" data-table-selection="single" data-table-wrap data-table-page-size="${filter.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar">
<h2>
<spring:message code="client.category.${category.tableName}"/> <span class="app-table-count">(<c:out value="${totals.totalRows()}"/>)</span></h2>
<div class="app-table-toolbar">
<c:if test="${permissions.contains('client.write')}"><a class="app-btn btn-neutral" href="<c:url value='${category.route}/archive'/>"><spring:message code="client.archive"/></a></c:if>
<c:if test="${permissions.contains('client.export')}">
<a class="app-btn btn-neutral" data-table-export="pdf" href="<c:url value='${category.route}/print.pdf'>
<c:param name='financialYearId' value='${filter.financialYearId}'/>
<c:param name='paymentStatusId' value='${filter.paymentStatusId}'/>
<c:param name='search' value='${filter.search}'/>
<c:param name='sort' value='${filter.sort}'/><c:param name='direction' value='${filter.direction}'/>
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
<th data-table-sort-key="name" data-table-sort-asc="name" data-table-sort-desc="name">
<spring:message code="common.name" />
</th>
<th data-table-sort-key="region" data-table-sort-asc="region" data-table-sort-desc="region">
<spring:message code="client.region" />
</th>
<th data-table-sort-key="entryDate" data-table-sort-asc="entryDate" data-table-sort-desc="entryDate">
<spring:message code="client.entryDate" />
</th>
<th data-table-sort-key="expireDate" data-table-sort-asc="expireDate" data-table-sort-desc="expireDate">
<spring:message code="client.expireDate" />
</th>
<th data-table-sort-key="annualFee" data-table-sort-asc="annualFee" data-table-sort-desc="annualFee">
<spring:message code="client.annualFee" />
</th>
<th data-table-sort-key="amount" data-table-sort-asc="amount" data-table-sort-desc="amount">
<spring:message code="client.amountPaid" />
</th>
<th data-table-sort-key="financialYear" data-table-sort-asc="financialYear" data-table-sort-desc="financialYear">
<spring:message code="report.year" />
</th>
<th data-table-sort-key="paymentStatus" data-table-sort-asc="paymentStatus" data-table-sort-desc="paymentStatus">
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
<a class="resource-table-id-link" href="<c:url value='${category.route}/${row.id()}'/>"><c:out value="${row.clientName()}"/></a>
</td>
<td>
<c:out value="${row.region()}"/>
</td>
<td>
<c:out value="${appTime:display(row.entryDate())}"/>
</td>
<td>
<c:out value="${appTime:display(row.expireDate())}"/>
</td>
<td data-table-number>
<c:out value="${row.annualFee()}"/>
</td>
<td data-table-number>
<c:out value="${row.amount()}"/>
</td>
<td>
<c:out value="${row.financialYear()}"/>
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
<td colspan="9">
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
<c:param name="page" value="${filter.page+1}"/>
</c:url>
<a data-page-direction="next" href="<c:out value='${pageUrl}'/>">next</a>
</c:if>
</div>
</section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
