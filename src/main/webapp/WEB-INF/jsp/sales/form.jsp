<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="${formTitle}"/></h1></div>
<c:url var="saveUrl" value="${formAction}"/>
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true" data-unsaved-draft="${unsavedDraft ? 'true' : 'false'}">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><form:hidden path="version"/><form:hidden path="requestKey"/>
<fieldset class="fcms-form-section"><legend><spring:message code="common.client"/></legend>
<div class="fcms-field"><label for="clientType"><spring:message code="sales.clientType"/></label><form:select id="clientType" path="clientType"><c:forEach items="${clientTypes}" var="option"><form:option value="${option}"><spring:message code="sales.type.${option}"/></form:option></c:forEach></form:select><form:errors path="clientType" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="clientName"><spring:message code="common.client"/></label><form:input id="clientName" path="clientName" type="text" maxlength="150" required="true"/><form:errors path="clientName" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="region"><spring:message code="sales.region"/></label><form:input id="region" path="region" type="text" maxlength="100"/><form:errors path="region" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="contactName"><spring:message code="sales.contactName"/></label><form:input id="contactName" path="contactName" type="text" maxlength="150"/><form:errors path="contactName" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="phone"><spring:message code="sales.phone"/></label><form:input id="phone" path="phone" type="tel" maxlength="40"/><form:errors path="phone" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="email"><spring:message code="common.email"/></label><form:input id="email" path="email" type="email" maxlength="190"/><form:errors path="email" cssClass="fcms-error"/></div>
</fieldset>
<fieldset class="fcms-form-section"><legend><spring:message code="sales.billing"/></legend>
<div class="fcms-field"><label for="billingAddress"><spring:message code="sales.billingAddress"/></label><form:input id="billingAddress" path="billingAddress" type="text" maxlength="250"/><form:errors path="billingAddress" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="billingCity"><spring:message code="sales.billingCity"/></label><form:input id="billingCity" path="billingCity" type="text" maxlength="150"/><form:errors path="billingCity" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="billingTin"><spring:message code="sales.billingTin"/></label><form:input id="billingTin" path="billingTin" type="text" maxlength="60"/><form:errors path="billingTin" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="paymentMethod"><spring:message code="sales.paymentMethod"/></label><form:select id="paymentMethod" path="paymentMethod"><c:forEach items="${paymentMethods}" var="option"><form:option value="${option}"><spring:message code="sales.payment.${option}"/></form:option></c:forEach></form:select><form:errors path="paymentMethod" cssClass="fcms-error"/></div>
</fieldset>
<fieldset class="fcms-form-section"><legend><spring:message code="sales.document"/></legend>
<div class="fcms-field"><label for="documentNumber"><spring:message code="${kind.claim}.number"/></label><form:input id="documentNumber" path="documentNumber" type="text" maxlength="60" required="true"/><form:errors path="documentNumber" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="documentDate"><spring:message code="sales.documentDate"/></label><form:input id="documentDate" path="documentDate" type="date" required="true"/><form:errors path="documentDate" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="validUntil"><spring:message code="${kind.claim}.until"/></label><form:input id="validUntil" path="validUntil" type="date" required="true"/><form:errors path="validUntil" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="currency"><spring:message code="sales.currency"/></label><form:select id="currency" path="currency" data-form-currency="true"><c:forEach items="${currencies}" var="option"><form:option value="${option}"><c:out value="${option}"/></form:option></c:forEach></form:select><form:errors path="currency" cssClass="fcms-error"/></div>
</fieldset>
<section class="erp-resource-section"><h2><spring:message code="sales.items"/></h2><div class="fcms-section-body">
<c:forEach items="${form.items}" var="item" varStatus="loop">
<fieldset class="sales-line"><legend><spring:message code="sales.item"/> ${loop.count}</legend>
<div class="fcms-field"><label for="item-description-${loop.index}"><spring:message code="common.description"/></label>
<form:textarea id="item-description-${loop.index}" path="items[${loop.index}].description" rows="3" maxlength="4000" required="true"/><form:errors path="items[${loop.index}].description" cssClass="fcms-error"/></div>
<div class="fcms-field"><label for="item-amount-${loop.index}"><spring:message code="sales.amount"/> (<span data-form-currency-label><c:out value="${form.currency}"/></span>)</label>
<form:input id="item-amount-${loop.index}" path="items[${loop.index}].amount" type="number" step="0.01" min="0" max="999999999999.99" required="true"/><form:errors path="items[${loop.index}].amount" cssClass="fcms-error"/></div>
<c:if test="${fn:length(form.items)>1}"><button class="app-btn btn-neutral" type="submit" name="removeItem" value="${loop.index}" formnovalidate><spring:message code="sales.removeItem"/> ${loop.count}</button></c:if>
</fieldset>
</c:forEach>
<form:errors path="items" cssClass="fcms-error"/>
<c:if test="${fn:length(form.items)<50}"><div class="fcms-form-actions"><button class="app-btn btn-neutral" type="submit" name="addItem" value="true" formnovalidate><spring:message code="sales.addItem"/></button></div></c:if>
</div></section>
<fieldset class="fcms-form-section"><legend><spring:message code="sales.terms"/></legend><div class="fcms-field"><label for="terms"><spring:message code="sales.terms"/></label><form:textarea path="terms" id="terms" maxlength="2000" rows="4"/><form:errors path="terms" cssClass="fcms-error"/></div></fieldset>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert"/>
<div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='${baseUrl}'/>"><spring:message code="common.cancel"/></a><button class="app-btn btn-launch" type="submit"><spring:message code="common.save"/></button></div>
</form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>