<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<section class="erp-table-wrap" data-resource-table="${resourceKey}" data-table-selection="single" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar">
<h2><spring:message code="${kind.title}"/></h2>
<c:if test="${permissions.contains(kind.claim.concat('.write'))}"><a class="app-btn btn-launch" href="<c:url value='${baseUrl}/new'/>"><spring:message code="common.create"/></a></c:if>
</div>
<form method="get" action="<c:url value='${baseUrl}'/>">
<input type="hidden" name="sort" value="<c:out value='${query.sort}'/>"><input type="hidden" name="direction" value="<c:out value='${query.direction}'/>"><input type="hidden" name="size" value="${query.size}">
<label><spring:message code="common.search"/><input name="search" type="search" maxlength="100" value="<c:out value='${query.search}'/>"></label>
<label><spring:message code="sales.clientType"/><select name="status"><option value=""><spring:message code="common.all"/></option><c:forEach items="${clientTypes}" var="type"><option value="${type}" ${query.status==type?'selected':''}><spring:message code="sales.type.${type}"/></option></c:forEach></select></label>
<label><spring:message code="common.from"/><input name="from" type="date" value="<c:out value='${query.from}'/>"></label>
<label><spring:message code="common.to"/><input name="to" type="date" value="<c:out value='${query.to}'/>"></label>
<button class="app-btn btn-neutral" type="submit"><spring:message code="common.apply"/></button><a class="app-btn btn-neutral" href="<c:url value='${baseUrl}'/>"><spring:message code="common.clear"/></a>
</form>
<div class="erp-table-scroll"><table class="erp-table">
<thead><tr>
<th data-table-sort-key="documentNumber" data-table-sort-asc="documentNumber" data-table-sort-desc="documentNumber"><spring:message code="${kind.claim}.number"/></th>
<th data-table-sort-key="clientName" data-table-sort-asc="clientName" data-table-sort-desc="clientName"><spring:message code="common.client"/></th>
<th data-table-sort-key="clientType" data-table-sort-asc="clientType" data-table-sort-desc="clientType"><spring:message code="sales.clientType"/></th>
<th data-table-sort-key="documentDate" data-table-sort-asc="documentDate" data-table-sort-desc="documentDate"><spring:message code="sales.documentDate"/></th>
<th data-table-sort-key="validUntil" data-table-sort-asc="validUntil" data-table-sort-desc="validUntil"><spring:message code="${kind.claim}.until"/></th>
<th data-table-sort-key="currency" data-table-sort-asc="currency" data-table-sort-desc="currency"><spring:message code="sales.currency"/></th>
<th data-table-sort-key="amount" data-table-sort-asc="amount" data-table-sort-desc="amount"><spring:message code="sales.amount"/></th>
<th data-table-actions-header><spring:message code="common.actions"/></th>
</tr></thead><tbody>
<c:forEach items="${records.rows()}" var="row"><tr>
<td><a class="resource-table-id-link" href="<c:url value='${baseUrl}/${row.id()}'/>"><c:out value="${row.documentNumber()}"/></a></td>
<td><c:out value="${row.clientName()}"/></td><td><spring:message code="sales.type.${row.clientType()}"/></td>
<td><c:out value="${appTime:date(row.documentDate())}"/></td><td><c:out value="${appTime:date(row.validUntil())}"/></td>
<td><c:out value="${row.currency()}"/></td><td><fmt:formatNumber value="${row.amount()}" minFractionDigits="2" maxFractionDigits="2"/></td>
<td><a class="app-btn btn-neutral" href="<c:url value='${baseUrl}/${row.id()}'/>"><spring:message code="common.view"/></a>
<c:if test="${permissions.contains(kind.claim.concat('.write'))}"><a class="app-btn btn-neutral" href="<c:url value='${baseUrl}/${row.id()}/edit'/>"><spring:message code="common.edit"/></a></c:if>
<c:if test="${permissions.contains(kind.claim.concat('.export'))}">
<a class="app-btn btn-neutral" target="_blank" rel="noopener" href="<c:url value='${baseUrl}/${row.id()}/document.pdf'/>"><ui:reportExport format="print"/></a><a class="app-btn btn-neutral" href="<c:url value='${baseUrl}/${row.id()}/document.pdf?download=true'/>"><ui:reportExport format="pdf"/></a></c:if></td>
</tr></c:forEach>
<c:if test="${empty records.rows()}"><tr><td colspan="8"><spring:message code="common.noRecords"/></td></tr></c:if>
</tbody></table></div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
</section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
