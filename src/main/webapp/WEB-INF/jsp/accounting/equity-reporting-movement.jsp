<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="gl.equity.classify"/></h1></div>
<c:url var="saveUrl" value="/accounting/equity-reporting/movements/${journalId}"/>
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><form:hidden path="requestKey"/><form:hidden path="baseId"/>
<fieldset class="fcms-form-section"><legend><spring:message code="gl.equity.movement"/></legend>
<dl class="fcms-details"><div><dt><spring:message code="gl.voucherId"/></dt><dd><a href="<c:url value='/accounting/journals/${journalId}'/>"><c:out value="${journalId}"/></a></dd></div></dl>
<div class="fcms-field"><label for="category"><spring:message code="common.type"/></label><form:select id="category" path="category"><c:forEach items="${categories}" var="category"><form:option value="${category}"><spring:message code="gl.equity.movement.${category}"/></form:option></c:forEach></form:select><form:errors path="category" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="reason"><spring:message code="common.reason"/></label><form:textarea id="reason" path="reason" maxlength="500" required="required"/><form:errors path="reason" cssClass="fcms-error"/></div>
</fieldset><form:errors path="" element="div" cssClass="fcms-alert" role="alert"/>
<div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/accounting/equity-reporting/movements'/>"><spring:message code="common.cancel"/></a><button class="app-btn btn-launch"><spring:message code="gl.reporting.propose"/></button></div>
</form:form><%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
