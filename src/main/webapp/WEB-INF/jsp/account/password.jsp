<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="account.changePassword" />
</h1>
</div>
<c:set var="formAction" value="/account/password"/>
<c:url var="saveUrl" value="${formAction}" />
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<fieldset class="fcms-form-section">
<legend><spring:message code="common.password"/></legend>
<div class="fcms-field">
<label for="currentPassword">
<spring:message code="account.currentPassword" />
</label>
<form:password id="currentPassword" path="currentPassword" autocomplete="current-password" required="required"/>
<form:errors path="currentPassword" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="newPassword">
<spring:message code="account.newPassword" />
</label>
<form:password id="newPassword" path="newPassword" autocomplete="new-password" minlength="12" maxlength="72" required="required"/>
<form:errors path="newPassword" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="confirmPassword">
<spring:message code="common.confirmPassword" />
</label>
<form:password id="confirmPassword" path="confirmPassword" autocomplete="new-password" minlength="12" maxlength="72" required="required"/>
<form:errors path="confirmPassword" cssClass="fcms-error" />
</div>
</fieldset>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert" />
<div class="fcms-form-actions">
<a class="app-btn btn-neutral" href="<c:url value='/account' />">
<spring:message code="common.cancel" />
</a>
<button class="app-btn btn-launch" type="submit">
<spring:message code="common.save" />
</button>
</div>
</form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
