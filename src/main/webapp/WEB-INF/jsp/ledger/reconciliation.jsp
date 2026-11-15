<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="ledger.reconciliation"/></h1></div>
<section class="erp-table-wrap">
<div class="erp-table-scroll"><table class="erp-table">
<thead><tr>
<th><spring:message code="ledger.account"/></th>
<th><spring:message code="ledger.storedAmount"/></th>
<th><spring:message code="ledger.postedAmount"/></th>
<th><spring:message code="ledger.storedUnits"/></th>
<th><spring:message code="ledger.postedUnits"/></th>
<th><spring:message code="common.status"/></th>
</tr></thead><tbody>
<c:forEach items="${reconciliations}" var="row"><tr>
<td><spring:message code="${row.kind().title}"/></td>
<td><c:out value="${row.storedAmount()}"/></td><td><c:out value="${row.postedAmount()}"/></td>
<td><c:out value="${row.storedUnits()}"/></td><td><c:out value="${row.postedUnits()}"/></td>
<td><c:choose><c:when test="${row.matches()}"><spring:message code="ledger.matched"/></c:when><c:otherwise><strong class="fcms-error"><spring:message code="ledger.mismatch"/></strong></c:otherwise></c:choose></td>
</tr></c:forEach>
</tbody></table></div></section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
