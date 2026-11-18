<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/grouped-resource-table.css?v=20261116-coa-tree2'/>">
<nav data-table-context-for="gl-accounts" ${empty groupPath?'hidden':''} class="erp-page-path" aria-label="<spring:message code='common.breadcrumb'/>"><a href="<c:url value='/accounting/accounts'/>"><spring:message code="gl.accounts"/></a><c:forEach items="${groupPath}" var="ancestor" varStatus="pathStatus"><span aria-hidden="true"> / </span><c:choose><c:when test="${pathStatus.last}"><span aria-current="page"><c:out value="${ancestor.code()}"/></span></c:when><c:otherwise><a href="<c:url value='/accounting/accounts?parentId=${ancestor.id()}'/>"><c:out value="${ancestor.code()}"/></a></c:otherwise></c:choose></c:forEach></nav>
<div class="accounting-workspace accounting-resource-workspace">
<section id="coa-register" class="erp-table-wrap" data-resource-table="${resourceKey}" data-coa-tree data-table-selection="single" data-resource-split data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100" data-table-sync-query="true"
 data-children-url="<c:url value='/accounting/accounts/children'/>" data-base-url="<c:url value='/accounting/accounts'/>"
 data-expand="<spring:message code='gl.group.expand'/>" data-collapse="<spring:message code='gl.group.collapse'/>"
 data-load-error="<spring:message code='table.loadError'/>" data-loading="<spring:message code='gl.coa.loading'/>" data-empty="<spring:message code='gl.coa.emptyChildren'/>" data-retry="<spring:message code='gl.coa.retry'/>">
 <div class="app-table-titlebar coa-titlebar">
  <h2><spring:message code="gl.accounts"/> <span class="app-table-count">(${chartTotal})</span></h2>
  <div class="app-table-toolbar accounting-actions">
   <button type="button" class="app-btn btn-neutral" data-table-command data-coa-command="view" disabled><spring:message code="common.view"/></button>
   <button type="button" class="app-btn btn-neutral" data-table-command data-coa-command="children" disabled><spring:message code="gl.childAccounts"/></button>
   <c:if test="${permissions.contains('accounting.setup')}">
    <button type="button" class="app-btn btn-neutral" data-table-command data-coa-command="edit" disabled><spring:message code="common.edit"/></button>
    <form method="post" data-table-command data-coa-state><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><input type="hidden" name="version"><input type="hidden" name="active"><button class="app-btn btn-neutral" type="submit" disabled data-coa-command="state" data-activate="<spring:message code='common.activate'/>" data-deactivate="<spring:message code='common.deactivate'/>"><spring:message code="common.deactivate"/></button></form>
   </c:if>
   <c:if test="${permissions.contains('accounting.report')}"><button type="button" class="app-btn btn-neutral" data-table-command data-coa-command="ledger" disabled><spring:message code="gl.generalLedger"/></button></c:if>
   <c:if test="${permissions.contains('accounting.setup')}"><button type="button" class="app-btn btn-launch" data-coa-create disabled data-group-label="<spring:message code='gl.coa.addGroup'/>" data-posting-label="<spring:message code='gl.coa.newPosting'/>"><spring:message code="gl.coa.addGroup"/></button><noscript><a class="app-btn btn-launch" href="<c:url value='/accounting/accounts/new?parentId=${parentId}'/>"><spring:message code="common.create"/></a></noscript></c:if>
  </div>
 </div>
 <%@ include file="/WEB-INF/jsp/accounting/account-filters.jspf" %>
 <span class="sr-only" role="status" aria-live="polite" data-coa-status></span>
 <div class="erp-table-scroll"><table class="erp-table coa-table">
  <colgroup><col class="coa-selection-column"><col class="coa-code-column"><col><col class="coa-parent-column"><col class="coa-class-column"><col class="coa-kind-column"><col class="coa-balance-column"><col class="coa-status-column"></colgroup>
  <thead><tr><th scope="col" class="resource-table-selection"><span class="sr-only"><spring:message code="gl.group.select"/></span></th>
   <c:forTokens items="code,name,parent,classification,kind,normal,status" delims="," var="column">
    <c:url var="sortUrl" value="${baseUrl}"><c:param name="parentId" value="${parentId}"/><c:param name="search" value="${query.search}"/><c:param name="classification" value="${query.classification}"/><c:param name="kind" value="${query.kind}"/><c:param name="status" value="${query.status}"/><c:param name="sort" value="${column}"/><c:param name="direction" value="${query.sort==column and query.direction=='asc'?'desc':'asc'}"/><c:param name="size" value="${query.size}"/></c:url>
    <th scope="col" data-table-sort-key="${column}" data-table-sort-asc="${column}" data-table-sort-desc="${column}" aria-sort="${query.sort==column?(query.direction=='asc'?'ascending':'descending'):'none'}"><a data-table-sort href="<c:out value='${sortUrl}'/>"><spring:message code="gl.coa.column.${column}"/></a></th>
   </c:forTokens>
  </tr></thead>
  <tbody><%@ include file="/WEB-INF/jsp/accounting/account-rows.jsp" %><c:if test="${empty records.rows()}"><tr><td colspan="8"><spring:message code="${query.filtered()?'table.noMatches':empty parentId?'common.noRecords':'gl.coa.emptyChildren'}"/></td></tr></c:if></tbody>
 </table></div>
 <%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
</section>
</div>
<script defer src="<c:url value='/js/grouped-resource-table.js?v=20261117-coa-review'/>"></script>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
