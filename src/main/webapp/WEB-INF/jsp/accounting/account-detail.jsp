<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="gl.account.details"/></h1></div>
<div class="app-actions">
<c:if test="${permissions.contains('accounting.setup') and account.depth()>1}"><a class="app-btn btn-neutral" href="<c:url value='/accounting/accounts/${account.id()}/edit'/>"><spring:message code="common.edit"/></a></c:if>
<c:if test="${account.depth()<4}"><a class="app-btn btn-neutral" href="<c:url value='/accounting/accounts?parentId=${account.id()}'/>"><spring:message code="gl.childAccounts"/></a><c:if test="${permissions.contains('accounting.setup') and account.active()}"><a class="app-btn btn-launch" href="<c:url value='/accounting/accounts/new?parentId=${account.id()}'/>"><spring:message code="gl.account.create"/></a></c:if></c:if>
<c:if test="${permissions.contains('accounting.report') and account.depth()==4}"><a class="app-btn btn-neutral" href="<c:url value='/accounting/accounts/${account.id()}/ledger'/>"><spring:message code="gl.generalLedger"/></a></c:if>
</div>
<section class="erp-resource-section"><h2><spring:message code="common.details"/></h2><div class="fcms-section-body"><dl class="fcms-details">
<div><dt><spring:message code="gl.code"/></dt><dd><c:out value="${account.code()}"/></dd></div>
<div><dt><spring:message code="common.name"/></dt><dd><c:out value="${account.name()}"/></dd></div>
<div><dt><spring:message code="gl.parent"/></dt><dd><c:out value="${account.parentName()}"/></dd></div>
<div><dt><spring:message code="gl.classification"/></dt><dd><spring:message code="gl.class.${account.classification()}"/></dd></div>
<div><dt><spring:message code="gl.normal"/></dt><dd><spring:message code="gl.${account.normalBalance()}"/></dd></div>
<div><dt><spring:message code="common.status"/></dt><dd><spring:message code="status.${account.active()?'ACTIVE':'INACTIVE'}"/></dd></div>
</dl>
<c:if test="${permissions.contains('accounting.setup') and account.depth()>1}"><form method="post" action="<c:url value='/accounting/accounts/${account.id()}/state'/>"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><input type="hidden" name="version" value="${account.version()}"><input type="hidden" name="active" value="${not account.active()}"><button class="app-btn btn-neutral" type="submit"><spring:message code="${account.active()?'common.deactivate':'common.activate'}"/></button></form></c:if>
</div></section>
<section class="erp-resource-section"><h2><spring:message code="gl.reporting.title"/></h2><div class="fcms-section-body">
<c:choose><c:when test="${empty reporting.effective()}"><span><spring:message code="gl.reporting.notConfigured"/></span></c:when><c:otherwise>
<c:set var="effective" value="${reporting.effective()}"/><dl class="fcms-details">
<div><dt><spring:message code="gl.reporting.current"/></dt><dd><a class="resource-table-id-link" href="<c:url value='/accounting/accounts/${account.id()}/reporting/${effective.id()}'/>">#<c:out value="${effective.id()}"/></a></dd></div>
<c:if test="${reporting.sectionEditable()}"><div><dt><spring:message code="gl.reporting.section"/></dt><dd><spring:message code="gl.presentation.section.${empty effective.proposed().section()?'UNCLASSIFIED':effective.proposed().section()}"/></dd></div></c:if>
<div><dt><spring:message code="gl.reporting.noteNumber"/></dt><dd><c:choose><c:when test="${effective.proposed().noteNumber()==null}"><spring:message code="gl.reporting.inherit"/></c:when><c:when test="${effective.proposed().noteNumber()==0}"><spring:message code="gl.reporting.none"/></c:when><c:otherwise><c:out value="${effective.proposed().noteNumber()}"/></c:otherwise></c:choose></dd></div>
<c:if test="${not empty effective.proposed().noteText()}"><div><dt><spring:message code="gl.reporting.noteText"/></dt><dd><c:out value="${effective.proposed().noteText()}"/></dd></div></c:if>
</dl></c:otherwise></c:choose>
<div class="app-actions"><c:if test="${reporting.canPropose()}"><a class="app-btn btn-launch" href="<c:url value='/accounting/accounts/${account.id()}/reporting/new'/>"><spring:message code="gl.reporting.propose"/></a></c:if>
<c:choose><c:when test="${not empty reporting.pending()}"><a class="app-btn btn-neutral" href="<c:url value='/accounting/accounts/${account.id()}/reporting/${reporting.pending().id()}'/>"><spring:message code="gl.reporting.pending"/></a></c:when><c:when test="${reporting.pendingExists()}"><span><spring:message code="gl.reporting.pending"/></span></c:when></c:choose></div>
</div></section>
<section class="erp-table-wrap" data-resource-table="gl-reporting-history" data-table-selection="none" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100">
<div class="app-table-titlebar"><h2><spring:message code="gl.reporting.history"/></h2></div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %>
<div class="erp-table-scroll"><table class="erp-table"><thead><tr>
<th data-table-sort-key="id" data-table-sort-asc="id" data-table-sort-desc="id"><spring:message code="gl.reporting.id"/></th><th data-table-sort-key="state" data-table-sort-asc="state" data-table-sort-desc="state"><spring:message code="common.status"/></th><th data-table-sort-key="maker" data-table-sort-asc="maker" data-table-sort-desc="maker"><spring:message code="gl.reporting.maker"/></th><th data-table-sort-key="created" data-table-sort-asc="created" data-table-sort-desc="created"><spring:message code="gl.reporting.created"/></th><th data-table-sort-key="reviewed" data-table-sort-asc="reviewed" data-table-sort-desc="reviewed"><spring:message code="gl.reporting.reviewed"/></th>
</tr></thead><tbody><c:forEach items="${records.rows()}" var="row"><tr>
<td><a class="resource-table-id-link" href="<c:url value='/accounting/accounts/${account.id()}/reporting/${row.id()}'/>">#<c:out value="${row.id()}"/></a></td>
<td><spring:message code="status.${row.state()}"/></td><td><c:out value="${row.makerName()}"/></td><td><c:out value="${appTime:display(row.createdAt())}"/></td><td><c:out value="${appTime:display(row.reviewedAt())}"/></td>
</tr></c:forEach><c:if test="${empty records.rows()}"><tr><td colspan="5"><spring:message code="common.noRecords"/></td></tr></c:if></tbody></table></div>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %>
</section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
