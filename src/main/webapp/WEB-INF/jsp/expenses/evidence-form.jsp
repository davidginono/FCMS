<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="expense.evidence.prepare"/></h1></div>
<form:form method="post" action="${pageContext.request.contextPath}${formAction}" modelAttribute="form" cssClass="fcms-form" data-resource-form-page="true" data-financial-form="true">
<form:errors path="*" cssClass="fcms-alert" element="div"/><form:hidden path="requestKey"/>
<%@ include file="/WEB-INF/jsp/expenses/payment-summary.jspf" %>
<section class="erp-resource-section"><h2><spring:message code="expense.evidence.title"/></h2><div class="fcms-section-body">
<div class="fcms-field"><label for="paidOn"><spring:message code="client.datePaid"/></label><form:input path="paidOn" id="paidOn" type="date" required="required"/><form:errors path="paidOn" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="payerId"><spring:message code="expense.evidence.payer"/></label><form:select path="payerId" id="payerId" data-choice-url="${pageContext.request.contextPath}/finance/expenses/evidence/payers"><form:option value=""/><form:options items="${payers}" itemValue="id" itemLabel="name"/></form:select><form:errors path="payerId" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="paymentReference"><spring:message code="expense.paymentReference"/></label><form:input path="paymentReference" id="paymentReference" maxlength="100" required="required"/><form:errors path="paymentReference" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="documentReference"><spring:message code="expense.evidence.document"/></label><form:input path="documentReference" id="documentReference" maxlength="200" required="required"/><form:errors path="documentReference" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="explanation"><spring:message code="common.reason"/></label><form:textarea path="explanation" id="explanation" maxlength="1000" required="required"/><form:errors path="explanation" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="openingPolicyReference"><spring:message code="expense.evidence.openingPolicy"/></label><form:input path="openingPolicyReference" id="openingPolicyReference" maxlength="200"/><form:errors path="openingPolicyReference" cssClass="fcms-error"/></div>
</div></section><div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/expenses/${payment.id()}/evidence'/>"><spring:message code="common.cancel"/></a><button type="submit" class="app-btn btn-launch"><spring:message code="common.submit"/></button></div></form:form>
<%@ include file="/WEB-INF/jsp/reusableuicomponents/form-support.jspf" %><%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
