<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="import.title"/></h1>
<a class="app-btn btn-neutral" download href="<c:url value='/clients/saccos/import/template.csv'/>"><spring:message code="import.template"/></a></div>
<form class="fcms-form" data-resource-form-page data-csv-import method="post" enctype="multipart/form-data" action="<c:url value='/clients/saccos/import'/>"
 data-csrf-header="${_csrf.headerName}" data-unavailable="<spring:message code='import.error.unavailable' htmlEscape='true'/>" data-denied="<spring:message code='error.denied' htmlEscape='true'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="requestKey" value="<c:out value='${upload.requestKey}'/>">
<fieldset class="fcms-form-section"><legend><spring:message code="import.file"/></legend>
<div class="fcms-field"><label for="importFile"><spring:message code="import.file"/></label>
<input id="importFile" name="file" type="file" accept=".csv,text/csv" required aria-describedby="importFileError">
<div id="importFileError" data-import-file-error class="${empty fileError?'':'fcms-error'}" ${empty fileError?'hidden':''}><c:out value="${fileError}"/></div></div>
</fieldset>
<details class="fcms-form-section"><summary><spring:message code="import.columns"/></summary>
<div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="import.column"/></th><th><spring:message code="common.name"/></th><th><spring:message code="import.format"/></th><th><spring:message code="import.required"/></th></tr></thead>
<tbody><c:forEach items="${columns}" var="column"><tr><td><c:out value="${column.header()}"/></td><td><spring:message code="${column.label()}"/></td><td><c:choose><c:when test="${column.format()=='255'}"><spring:message code="import.textFormat"/></c:when><c:otherwise><c:out value="${column.format()}"/></c:otherwise></c:choose></td><td><spring:message code="${column.required()?'common.yes':'common.no'}"/></td></tr></c:forEach></tbody></table></div>
</details>
<div data-import-form-error class="fcms-alert" role="alert" tabindex="-1" ${empty formError?'hidden':''}><c:out value="${formError}"/></div>
<div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/clients/saccos'/>"><spring:message code="common.cancel"/></a><button class="app-btn btn-launch" type="submit"><spring:message code="import.preview"/></button></div>
</form>
<c:if test="${not empty drafts}"><section class="erp-resource-section"><h2><spring:message code="import.drafts"/></h2><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th><spring:message code="import.batch"/></th><th><spring:message code="common.date"/></th><th><spring:message code="import.rows"/></th><th><spring:message code="client.annualFee"/></th><th><spring:message code="client.amountPaid"/></th></tr></thead><tbody>
<c:forEach items="${drafts}" var="draft"><tr><td><a class="resource-table-id-link" href="<c:url value='/clients/saccos/import/${draft.token()}'/>">#<c:out value="${draft.id()}"/></a></td><td><c:out value="${appTime:display(draft.createdAt())}"/></td><td><c:out value="${draft.rows()}"/></td><td><fmt:formatNumber value="${draft.annualFee()}" minFractionDigits="2" maxFractionDigits="2"/></td><td><fmt:formatNumber value="${draft.paid()}" minFractionDigits="2" maxFractionDigits="2"/></td></tr></c:forEach>
</tbody></table></div></section></c:if>
<script defer src="<c:url value='/js/saccos-import.js?v=20261115-import1'/>"></script>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
