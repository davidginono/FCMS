<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<spring:htmlEscape defaultHtmlEscape="true"/>
<c:forEach items="${records.rows()}" var="item"><c:set var="row" value="${item.account()}"/>
 <tr data-coa-row data-id="${row.id()}" data-depth="0" data-expandable="${item.expandable()}" data-add-group="${item.addGroup()}" data-add-posting="${item.addPosting()}" data-editable="${item.editable()}" data-lifecycle="${item.lifecycle()}" data-ledger="${item.ledger()}" data-active="${row.active()}" data-version="${row.version()}" class="${item.expandable()?'coa-group-row':''}">
  <td class="resource-table-selection"><input type="checkbox" name="coa-selection" value="${row.id()}" aria-label="<spring:message code='gl.group.select'/> <c:out value='${row.code()}'/>"></td>
  <td class="grouped-resource-code"><div class="coa-tree-code">
   <c:if test="${item.expandable()}"><a class="coa-tree-toggle" data-coa-toggle href="<c:url value='/accounting/accounts?parentId=${row.id()}'/>" role="button" aria-expanded="false" aria-label="<spring:message code='gl.group.expand'/> <c:out value='${row.code()}'/>"><svg viewBox="0 0 16 16" aria-hidden="true"><path d="M5 2L11 8L5 14"/></svg></a></c:if>
   <a class="resource-table-id-link" href="<c:url value='/accounting/accounts/${row.id()}'/>"><c:out value="${row.code()}"/></a>
  </div></td>
  <td data-coa-name><c:out value="${row.name()}"/></td><td><c:out value="${row.parentName()}"/></td>
  <td><spring:message code="gl.class.${row.classification()}"/></td><td><spring:message code="gl.coa.kind.${item.kind()}"/></td>
  <td><spring:message code="gl.${row.normalBalance()}"/></td><td><span data-resource-status="${row.active()?'ACTIVE':'INACTIVE'}"><spring:message code="status.${row.active()?'ACTIVE':'INACTIVE'}"/></span></td>
 </tr>
</c:forEach>
<c:if test="${childFragment and records.hasNext()}"><c:url var="moreUrl" value="${baseUrl}"><c:param name="parentId" value="${parentId}"/><c:param name="page" value="${records.page()+1}"/><c:param name="search" value="${query.search}"/><c:param name="classification" value="${query.classification}"/><c:param name="kind" value="${query.kind}"/><c:param name="status" value="${query.status}"/><c:param name="sort" value="${query.sort}"/><c:param name="direction" value="${query.direction}"/><c:param name="size" value="${query.size}"/></c:url><tr data-coa-more="${records.page()+1}"><td colspan="8"><a class="resource-table-id-link" data-coa-load-more href="<c:out value='${moreUrl}'/>"><spring:message code="gl.group.loadMore"/></a></td></tr></c:if>
