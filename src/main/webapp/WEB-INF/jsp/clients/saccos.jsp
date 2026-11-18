<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<c:set var="resourceKey" value="client-saccos"/>
<c:set var="baseUrl" value="${category.route}"/>
<section class="erp-table-wrap" data-resource-table="client-saccos" data-table-selection="single" data-table-wrap data-table-page-size="${filter.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar">
<h2>
<c:out value="${category.title}"/> <span class="app-table-count">(<c:out value="${totals.totalRows()}"/>)</span></h2>
<div class="app-table-toolbar">
<c:if test="${permissions.contains('client.write')}"><a class="app-btn btn-neutral" href="<c:url value='${category.route}/archive'/>"><spring:message code="client.archive"/></a></c:if>
<c:if test="${permissions.contains('client.import')&&permissions.contains('client.read')&&permissions.contains('client.write')}"><a class="app-btn btn-neutral" href="<c:url value='/clients/saccos/import'/>"><spring:message code="import.action"/></a></c:if>
<c:if test="${permissions.contains('client.export')}">
<a class="app-btn btn-neutral" data-table-export="pdf" href="<c:url value='${category.route}/print.pdf'>
<c:param name='financialYearId' value='${filter.financialYearId}'/>
<c:param name='paymentStatusId' value='${filter.paymentStatusId}'/>
<c:param name='search' value='${filter.search}'/>
<c:param name='sort' value='${filter.sort}'/><c:param name='direction' value='${filter.direction}'/>
</c:url>">
<ui:reportExport format="pdf"/>
</a>
<c:url var="paymentPdfUrl" value="/clients/saccos/${filter.paymentStatusId==2?'unpaid':'paid'}/print.pdf">
<c:param name="financialYearId" value="${filter.financialYearId}"/>
<c:param name="search" value="${filter.search}"/>
<c:param name="sort" value="${filter.sort}"/><c:param name="direction" value="${filter.direction}"/>
</c:url>
<a class="app-btn btn-neutral" data-table-export="payment" href="<c:out value='${paymentPdfUrl}'/>" ${filter.paymentStatusId==1||filter.paymentStatusId==2?'':'hidden'}><ui:reportExport format="pdf" labelCode="client.paymentSummaryPdf"/></a>
</c:if>
<c:if test="${permissions.contains('client.export')}">
<c:url var="csvUrl" value="${category.route}/export.csv"><c:param name="financialYearId" value="${filter.financialYearId}"/><c:param name="paymentStatusId" value="${filter.paymentStatusId}"/><c:param name="search" value="${filter.search}"/><c:param name="sort" value="${filter.sort}"/><c:param name="direction" value="${filter.direction}"/></c:url>
<a class="app-btn btn-neutral" data-table-export="csv" href="<c:out value='${csvUrl}'/>"><ui:reportExport format="csv"/></a>
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
<spring:message code="client.paymentStatus" />
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
<%@ include file="client-register-table.jspf" %>
</section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
