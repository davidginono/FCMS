<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="gl.equity.configure"/></h1></div>
<c:url var="saveUrl" value="/accounting/equity-reporting"/>
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true" data-unsaved-draft="${unsavedDraft ? 'true' : 'false'}">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><form:hidden path="requestKey"/><form:hidden path="baseId"/>
<fieldset class="fcms-form-section"><legend><spring:message code="gl.equity.components"/></legend>
<c:forEach items="${form.components}" var="component" varStatus="i"><fieldset class="fcms-form-section"><legend><spring:message code="gl.equity.component"/> ${i.count}</legend>
<div class="fcms-field"><label for="component-name-${i.index}"><spring:message code="common.name"/></label><form:input id="component-name-${i.index}" path="components[${i.index}].name" maxlength="100" required="required"/><form:errors path="components[${i.index}].name" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="component-sw-${i.index}"><spring:message code="gl.equity.nameSw"/></label><form:input id="component-sw-${i.index}" path="components[${i.index}].nameSw" maxlength="100"/><form:errors path="components[${i.index}].nameSw" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="component-accounts-${i.index}"><spring:message code="gl.equity.accounts"/></label><form:input id="component-accounts-${i.index}" path="components[${i.index}].accounts" maxlength="20000" required="required"/><form:errors path="components[${i.index}].accounts" cssClass="fcms-error"/></div>
<div class="fcms-field"><label><form:checkbox path="components[${i.index}].retained"/><spring:message code="gl.presentation.retainedEarnings"/></label></div>
<button class="app-btn btn-neutral" name="operation" value="remove:${i.index}" formnovalidate><spring:message code="common.remove"/></button>
</fieldset></c:forEach>
<c:if test="${form.components.size()<9}"><button class="app-btn btn-neutral" name="operation" value="add" formnovalidate><spring:message code="common.add"/></button></c:if>
</fieldset>
<details class="erp-resource-section"><summary><spring:message code="gl.accounts"/></summary><div class="fcms-section-body"><c:forEach items="${choices}" var="account"><div><c:out value="${account.code()}"/> · <c:out value="${account.name()}"/></div></c:forEach></div></details>
<fieldset class="fcms-form-section"><legend><spring:message code="common.reason"/></legend><div class="fcms-field"><label for="reason"><spring:message code="common.reason"/></label><form:textarea id="reason" path="reason" maxlength="500" required="required"/><form:errors path="reason" cssClass="fcms-error"/></div></fieldset>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert"/>
<div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/accounting/equity-reporting'/>"><spring:message code="common.cancel"/></a><button class="app-btn btn-launch" name="operation" value="save"><spring:message code="gl.reporting.propose"/></button></div>
</form:form><%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
