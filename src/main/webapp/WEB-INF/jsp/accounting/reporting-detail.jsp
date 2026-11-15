<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<c:set var="revision" value="${reportingDecision.revision()}"/>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="gl.reporting.revision"/></h1></div>
<section class="erp-resource-section"><h2><spring:message code="common.details"/></h2><div class="fcms-section-body"><dl class="fcms-details">
<div><dt><spring:message code="gl.account"/></dt><dd><c:out value="${account.code()}"/> · <c:out value="${account.name()}"/></dd></div>
<div><dt><spring:message code="gl.reporting.id"/></dt><dd>#<c:out value="${revision.id()}"/></dd></div><div><dt><spring:message code="common.status"/></dt><dd><spring:message code="status.${revision.state()}"/></dd></div>
<div><dt><spring:message code="gl.reporting.maker"/></dt><dd><c:out value="${revision.makerName()}"/></dd></div><div><dt><spring:message code="gl.reporting.created"/></dt><dd><c:out value="${appTime:display(revision.createdAt())}"/></dd></div>
<div><dt><spring:message code="common.reason"/></dt><dd><c:out value="${revision.reason()}"/></dd></div>
<c:if test="${not empty revision.checkerId()}"><div><dt><spring:message code="gl.reporting.checker"/></dt><dd><c:out value="${revision.checkerName()}"/></dd></div><div><dt><spring:message code="gl.reporting.reviewed"/></dt><dd><c:out value="${appTime:display(revision.reviewedAt())}"/></dd></div><div><dt><spring:message code="gl.reviewComment"/></dt><dd><c:out value="${revision.reviewReason()}"/></dd></div></c:if>
<c:if test="${not empty revision.replacedBy()}"><div><dt><spring:message code="gl.reporting.replacement"/></dt><dd><a class="resource-table-id-link" href="<c:url value='/accounting/accounts/${account.id()}/reporting/${revision.replacedBy()}'/>">#<c:out value="${revision.replacedBy()}"/></a></dd></div></c:if>
</dl></div></section>
<section class="erp-table-wrap"><div class="app-table-titlebar"><h2><spring:message code="gl.reporting.title"/></h2></div><div class="erp-table-scroll"><table class="erp-table" data-table-sort-mode="none" data-table-sort-reason="reporting-comparison"><thead><tr><th><spring:message code="gl.reporting.field"/></th><th><spring:message code="gl.reporting.before"/></th><th><spring:message code="gl.reporting.proposed"/></th></tr></thead><tbody>
<tr><th scope="row"><spring:message code="gl.reporting.section"/></th><c:forEach items="${[revision.before(),revision.proposed()]}" var="setup"><td><c:choose><c:when test="${empty setup.section()}"><spring:message code="gl.reporting.inherit"/></c:when><c:otherwise><spring:message code="gl.presentation.section.${setup.section()}"/></c:otherwise></c:choose></td></c:forEach></tr>
<tr><th scope="row"><spring:message code="gl.reporting.noteNumber"/></th><c:forEach items="${[revision.before(),revision.proposed()]}" var="setup"><td><c:choose><c:when test="${setup.noteNumber()==null}"><spring:message code="gl.reporting.inherit"/></c:when><c:when test="${setup.noteNumber()==0}"><spring:message code="gl.reporting.none"/></c:when><c:otherwise><c:out value="${setup.noteNumber()}"/></c:otherwise></c:choose></td></c:forEach></tr>
<tr><th scope="row"><spring:message code="gl.reporting.noteText"/></th><td><c:out value="${revision.before().noteText()}"/></td><td><c:out value="${revision.proposed().noteText()}"/></td></tr>
</tbody></table></div></section>
<c:if test="${not empty reportingDecision.actions() or decisionFailed}">
<c:url var="decisionUrl" value="/accounting/accounts/${account.id()}/reporting/${revision.id()}/decision"/>
<form:form modelAttribute="decisionForm" method="post" action="${decisionUrl}" cssClass="fcms-form" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><form:hidden path="version"/>
<section class="erp-resource-section"><h2><spring:message code="gl.review"/></h2><div class="fcms-section-body"><div class="fcms-field"><label for="reason"><spring:message code="common.reason"/></label><form:textarea path="reason" id="reason" rows="2" maxlength="500" required="required"/><form:errors path="reason" cssClass="fcms-error"/></div></div></section>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert"/>
<div class="fcms-form-actions"><c:forEach items="${['cancel','reject','approve']}" var="action"><c:if test="${reportingDecision.actions().contains(action)}"><button class="app-btn ${action=='approve'?'btn-launch':'btn-neutral'}" type="submit" name="action" value="${action}"><spring:message code="common.${action}"/></button></c:if></c:forEach></div>
</form:form>
</c:if>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
