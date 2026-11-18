<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-entry.css?v=20261118-split2'/>">
<section class="erp-table-wrap" data-resource-table="${resourceKey}" data-config-register="activities" data-table-selection="single" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
 <div class="app-table-titlebar"><h2><spring:message code="config.activities"/></h2><div class="app-table-toolbar">
  <button type="button" class="app-btn btn-neutral" data-table-command data-config-command="open" disabled><spring:message code="common.view"/></button>
  <button type="button" class="app-btn btn-neutral" data-table-command data-config-command="transactions" disabled><spring:message code="config.transactions"/></button>
  <c:if test="${permissions.contains('accounting.setup')}"><button type="button" class="app-btn btn-neutral" data-table-command data-config-command="edit" disabled><spring:message code="common.edit"/></button><button type="button" class="app-btn btn-neutral" data-table-command data-config-command="state" data-activate="<spring:message code='common.activate'/>" data-deactivate="<spring:message code='common.deactivate'/>" disabled><spring:message code="common.deactivate"/></button><a class="app-btn btn-launch" data-config-open href="<c:url value='/accounting/config/activities?create=true'/>"><spring:message code="config.activity.create"/></a></c:if>
 </div></div>
 <%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
 <div class="erp-table-scroll"><table class="erp-table"><thead><tr><th scope="col" class="resource-table-selection"></th><th scope="col" data-table-sort-key="code" data-table-sort-asc="code" data-table-sort-desc="code"><spring:message code="gl.code"/></th><th scope="col" data-table-sort-key="name" data-table-sort-asc="name" data-table-sort-desc="name"><spring:message code="common.name"/></th><th scope="col" data-table-sort-key="transactions" data-table-sort-asc="transactions" data-table-sort-desc="transactions"><spring:message code="config.transactions"/></th><th scope="col" data-table-sort-key="status" data-table-sort-asc="status" data-table-sort-desc="status"><spring:message code="common.status"/></th></tr></thead><tbody>
 <c:forEach items="${records.rows()}" var="row"><tr data-config-row="${row.id()}" data-active="${row.active()}"><td class="resource-table-selection"><input type="checkbox" name="config-selection" aria-label="<spring:message code='gl.group.select'/> <c:out value='${row.code()}'/>"></td><td><a class="resource-table-id-link" href="<c:url value='/accounting/config/activities/${row.id()}'/>"><c:out value="${row.code()}"/></a></td><td><c:out value="${row.name()}"/></td><td><c:out value="${row.transactions()}"/></td><td><spring:message code="status.${row.active()?'ACTIVE':'INACTIVE'}"/></td></tr></c:forEach>
 <c:if test="${empty records.rows()}"><tr><td colspan="5"><spring:message code="common.noRecords"/></td></tr></c:if>
 </tbody></table></div><%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
 <span class="sr-only" role="status" data-config-selection-status></span>
</section>
<c:set var="configKind" value="activities"/><c:if test="${permissions.contains('accounting.setup')}"><%@ include file="/WEB-INF/jsp/accounting/config/create-modal.jspf" %></c:if>
<script defer src="<c:url value='/js/transaction-config.js?v=20261118-vouchers3'/>"></script>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
