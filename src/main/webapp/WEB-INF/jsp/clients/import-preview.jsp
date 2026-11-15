<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="import.preview"/></h1>
<a class="app-btn btn-neutral" href="<c:url value='/clients/saccos/import'/>"><spring:message code="import.new"/></a></div>
<section class="erp-resource-section"><h2><spring:message code="common.details"/></h2><div class="fcms-section-body"><dl class="fcms-details">
<div><dt><spring:message code="common.status"/></dt><dd><spring:message code="import.state.${preview.state()}"/></dd></div>
<div><dt><spring:message code="import.rows"/></dt><dd><c:out value="${preview.rowCount()}"/></dd></div>
<div><dt><spring:message code="client.annualFee"/></dt><dd><fmt:formatNumber value="${preview.annualFee()}" minFractionDigits="2" maxFractionDigits="2"/></dd></div>
<div><dt><spring:message code="client.amountPaid"/></dt><dd><fmt:formatNumber value="${preview.paid()}" minFractionDigits="2" maxFractionDigits="2"/></dd></div>
<div><dt><spring:message code="import.expires"/></dt><dd><c:out value="${appTime:display(preview.expiresAt())}"/></dd></div>
</dl></div></section>
<section class="erp-table-wrap" data-resource-table="saccos-import-preview" data-table-wrap data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar"><h2><spring:message code="client.category.saccos"/> <span class="app-table-count">(<c:out value="${preview.rowCount()}"/>)</span></h2></div>
<form method="get" action="<c:url value='${baseUrl}'/>"><input type="hidden" name="size" value="${query.size}"></form>
<div class="erp-table-scroll"><table class="erp-table"><thead><tr>
<th><spring:message code="import.line"/></th><th><spring:message code="common.name"/></th><th><spring:message code="client.region"/></th><th><spring:message code="report.year"/></th><th><spring:message code="common.status"/></th><th><spring:message code="client.annualFee"/></th><th><spring:message code="client.amountPaid"/></th><th><spring:message code="common.details"/></th>
</tr></thead><tbody>
<c:forEach items="${records.rows()}" var="row"><tr><td data-table-number><c:out value="${row.line()}"/></td>
<td><c:choose><c:when test="${row.clientId()!=null}"><a class="resource-table-id-link" href="<c:url value='/clients/saccos/${row.clientId()}'/>"><c:out value="${row.name()}"/></a></c:when><c:otherwise><c:out value="${row.name()}"/></c:otherwise></c:choose></td>
<td><c:out value="${row.region()}"/></td><td><c:out value="${row.year()}"/></td><td><spring:message code="status.${row.status()}"/></td>
<td data-table-number><fmt:formatNumber value="${row.annualFee()}" minFractionDigits="2" maxFractionDigits="2"/></td><td data-table-number><fmt:formatNumber value="${row.paid()}" minFractionDigits="2" maxFractionDigits="2"/></td>
<td><details><summary><spring:message code="common.details"/></summary><dl class="fcms-details"><c:forEach items="${row.facts()}" var="fact"><div><dt><spring:message code="${fact.label()}"/></dt><dd><c:choose><c:when test="${empty fact.value()}"><spring:message code="import.notProvided"/></c:when><c:otherwise><c:out value="${fact.value()}"/></c:otherwise></c:choose></dd></div></c:forEach></dl></details></td></tr></c:forEach>
<c:if test="${empty records.rows()}"><tr><td colspan="8"><spring:message code="common.noRecords"/></td></tr></c:if>
</tbody></table></div><%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
</section>
<c:if test="${not empty actionError}"><div class="fcms-alert" role="alert"><c:out value="${actionError}"/></div></c:if>
<c:if test="${preview.importable()}"><form class="fcms-form" data-financial-form method="post" action="<c:url value='${baseUrl}/commit'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/clients/saccos'/>"><spring:message code="common.back"/></a>
<button class="app-btn btn-neutral" type="submit" formaction="<c:url value='${baseUrl}/discard'/>"><spring:message code="import.discard"/></button>
<button class="app-btn btn-launch" type="submit"><spring:message code="import.commit" arguments="${preview.rowCount()}"/></button></div></form></c:if>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
