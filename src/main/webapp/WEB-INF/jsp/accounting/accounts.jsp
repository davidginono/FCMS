<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<link rel="stylesheet" href="<c:url value='/css/grouped-resource-table.css?v=20261103-grouped2'/>">
<section class="erp-table-wrap" data-resource-table="${resourceKey}" data-grouped-resources data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100" data-table-sync-query="true"
 data-select-label="<spring:message code='gl.group.select'/>" data-selection-label="<spring:message code='gl.group.selected'/>"
 data-expand-label="<spring:message code='gl.group.expand'/>" data-collapse-label="<spring:message code='gl.group.collapse'/>"
 data-load-error="<spring:message code='table.loadError'/>">
 <div class="app-table-titlebar">
  <h2><spring:message code="gl.accounts"/> <span class="app-table-count" data-grouped-counter>(${grouped.total()})</span></h2>
  <div class="app-table-toolbar">
   <button type="button" class="app-btn btn-neutral" data-table-command data-grouped-open disabled><spring:message code="common.view"/></button>
   <c:if test="${permissions.contains('accounting.setup')}"><a class="app-btn btn-launch" href="<c:url value='/accounting/accounts/new?parentId=${parentId}'/>"><spring:message code="common.create"/></a></c:if>
  </div>
 </div>
 <%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
 <span class="sr-only" role="status" aria-live="polite" data-grouped-status></span>
 <div class="erp-table-scroll">
  <table class="erp-table">
   <thead><tr>
    <th scope="col" class="resource-table-selection"><input type="checkbox" data-grouped-select-all aria-label="<spring:message code='gl.group.selectAll'/>" ${empty grouped.groups()?'disabled':''}></th>
    <th scope="col" data-table-identifier data-table-sort-key="code" data-table-sort-asc="code" data-table-sort-desc="code"><spring:message code="gl.code"/></th>
    <th scope="col" data-table-sort-key="name" data-table-sort-asc="name" data-table-sort-desc="name"><spring:message code="common.name"/></th>
    <th scope="col" data-table-sort-key="classification" data-table-sort-asc="classification" data-table-sort-desc="classification"><spring:message code="gl.classification"/></th>
    <th scope="col" data-table-sort-key="parent" data-table-sort-asc="parent" data-table-sort-desc="parent"><spring:message code="gl.parent"/></th>
    <th scope="col" data-table-sort-key="normal" data-table-sort-asc="normal" data-table-sort-desc="normal"><spring:message code="gl.normal"/></th>
    <th scope="col" data-table-sort-key="status" data-table-sort-asc="status" data-table-sort-desc="status"><spring:message code="common.status"/></th>
   </tr></thead>
   <tbody>
    <c:forEach items="${grouped.groups()}" var="accountGroup">
     <c:set var="groupKey" value="${accountGroup.classification()}"/>
     <tr class="grouped-resource-heading" data-grouped-heading="${groupKey}" data-group-total="${accountGroup.total()}">
      <td class="resource-table-selection"><input type="checkbox" data-grouped-select="${groupKey}" aria-label="<spring:message code='gl.group.select'/> <spring:message code='gl.class.${groupKey}'/>"></td>
      <th scope="row" colspan="6">
       <button type="button" class="grouped-resource-toggle" data-grouped-toggle="${groupKey}" aria-expanded="true" aria-label="<spring:message code='gl.group.collapse'/> <spring:message code='gl.class.${groupKey}'/>">
        <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M8 11L12 5H4L8 11Z"/></svg><span data-grouped-name><spring:message code="gl.class.${groupKey}"/></span>
       </button> <span class="app-table-count" data-grouped-count>(${accountGroup.total()})</span>
      </th>
     </tr>
     <c:forEach items="${accountGroup.accounts().rows()}" var="row">
      <tr data-account-group="${groupKey}" data-account-id="${row.id()}">
       <td class="resource-table-selection"><input type="checkbox" data-grouped-account="${row.id()}" aria-label="<spring:message code='gl.group.select'/> <c:out value='${row.code()}'/>"></td>
       <td class="grouped-resource-code"><a class="resource-table-id-link" href="<c:url value='/accounting/accounts/${row.id()}'/>"><c:out value="${row.code()}"/></a></td>
       <td><c:out value="${row.name()}"/></td><td><spring:message code="gl.class.${row.classification()}"/></td>
       <td><c:out value="${row.parentName()}"/></td><td><spring:message code="gl.${row.normalBalance()}"/></td>
       <td><spring:message code="status.${row.active()?'ACTIVE':'INACTIVE'}"/></td>
      </tr>
     </c:forEach>
     <c:if test="${accountGroup.accounts().hasNext() or accountGroup.accounts().page()>0}">
      <tr data-account-group="${groupKey}" class="grouped-resource-more"><td></td><td colspan="6">
       <c:if test="${accountGroup.accounts().page()>0}">
        <c:url var="previousGroupUrl" value="${baseUrl}"><c:param name="group" value="${groupKey}"/><c:param name="groupPage" value="${accountGroup.accounts().page()-1}"/><c:param name="parentId" value="${parentId}"/><c:param name="search" value="${query.search}"/><c:param name="status" value="${query.status}"/><c:param name="size" value="${query.size}"/><c:param name="sort" value="${query.sort}"/><c:param name="direction" value="${query.direction}"/></c:url>
        <a class="resource-table-id-link" data-grouped-previous href="<c:out value='${previousGroupUrl}'/>"><spring:message code="common.previous"/></a>
       </c:if>
       <c:if test="${accountGroup.accounts().hasNext()}">
        <c:url var="moreGroupUrl" value="${baseUrl}"><c:param name="group" value="${groupKey}"/><c:param name="groupPage" value="${accountGroup.accounts().page()+1}"/><c:param name="parentId" value="${parentId}"/><c:param name="search" value="${query.search}"/><c:param name="status" value="${query.status}"/><c:param name="size" value="${query.size}"/><c:param name="sort" value="${query.sort}"/><c:param name="direction" value="${query.direction}"/></c:url>
        <a class="resource-table-id-link" data-grouped-more="${groupKey}" href="<c:out value='${moreGroupUrl}'/>"><spring:message code="gl.group.loadMore"/></a>
       </c:if>
       <span class="fcms-error" role="alert" data-grouped-error></span>
      </td></tr>
     </c:if>
    </c:forEach>
    <c:if test="${empty grouped.groups()}"><tr><td colspan="7"><spring:message code="${empty query.search and empty query.status and empty parentId?'common.noRecords':'table.noMatches'}"/></td></tr></c:if>
   </tbody>
  </table>
 </div>
</section>
<script defer src="<c:url value='/js/grouped-resource-table.js?v=20261103-grouped2'/>"></script>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
