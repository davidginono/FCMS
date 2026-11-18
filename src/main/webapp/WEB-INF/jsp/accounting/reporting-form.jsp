<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="gl.reporting.propose"/></h1></div>
<c:url var="saveUrl" value="/accounting/accounts/${account.id()}/reporting"/>
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-create="true" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><form:hidden path="requestKey"/><form:hidden path="accountVersion"/><form:hidden path="baseId"/>
<section class="erp-resource-section"><h2><spring:message code="gl.account"/></h2><div class="fcms-section-body"><dl class="fcms-details">
<div><dt><spring:message code="gl.code"/></dt><dd><c:out value="${account.code()}"/></dd></div><div><dt><spring:message code="common.name"/></dt><dd><c:out value="${account.name()}"/></dd></div><div><dt><spring:message code="gl.classification"/></dt><dd><spring:message code="gl.class.${account.classification()}"/></dd></div>
</dl></div></section>
<section class="erp-resource-section"><h2><spring:message code="gl.reporting.title"/></h2><div class="fcms-section-body">
<c:choose><c:when test="${sectionEditable}"><div class="fcms-field"><label for="section"><spring:message code="gl.reporting.section"/></label><form:select path="section" id="section">
<c:choose><c:when test="${account.classification()=='ASSET'}"><form:option value="CURRENT_ASSETS"><spring:message code="gl.presentation.section.CURRENT_ASSETS"/></form:option><form:option value="NON_CURRENT_ASSETS"><spring:message code="gl.presentation.section.NON_CURRENT_ASSETS"/></form:option></c:when><c:otherwise><form:option value="CURRENT_LIABILITIES"><spring:message code="gl.presentation.section.CURRENT_LIABILITIES"/></form:option><form:option value="NON_CURRENT_LIABILITIES"><spring:message code="gl.presentation.section.NON_CURRENT_LIABILITIES"/></form:option></c:otherwise></c:choose>
<form:option value="UNCLASSIFIED"><spring:message code="gl.presentation.section.UNCLASSIFIED"/></form:option></form:select><form:errors path="section" cssClass="fcms-error"/></div></c:when><c:otherwise><form:hidden path="section"/></c:otherwise></c:choose>
<div class="fcms-field"><label for="noteNumber"><spring:message code="gl.reporting.noteNumber"/></label><spring:message code="gl.reporting.inherit" var="inheritLabel"/><form:input path="noteNumber" id="noteNumber" type="number" min="0" max="999" placeholder="${inheritLabel}"/><form:errors path="noteNumber" cssClass="fcms-error"/><span class="fcms-field-hint"><spring:message code="gl.reporting.noteHint"/></span></div>
<div class="fcms-field"><label for="noteText"><spring:message code="gl.reporting.noteText"/></label><form:textarea path="noteText" id="noteText" rows="5" maxlength="2000"/><form:errors path="noteText" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="reason"><spring:message code="common.reason"/></label><form:textarea path="reason" id="reason" rows="2" maxlength="500" required="required"/><form:errors path="reason" cssClass="fcms-error"/></div>
</div></section>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert"/>
<form:errors path="requestKey" element="div" cssClass="fcms-alert" role="alert"/><form:errors path="accountVersion" element="div" cssClass="fcms-alert" role="alert"/><form:errors path="baseId" element="div" cssClass="fcms-alert" role="alert"/><div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/accounting/accounts/${account.id()}'/>"><spring:message code="common.cancel"/></a><button class="app-btn btn-launch" type="submit" ${account.active()?'':'disabled'}><spring:message code="gl.reporting.submit"/></button></div>
</form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
