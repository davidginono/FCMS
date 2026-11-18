<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-entry.css?v=20261118-split2'/>">
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="config.template.edit"/></h1></div>
<section class="erp-resource-section"><h2><spring:message code="config.transaction"/></h2><div class="fcms-section-body"><dl class="fcms-details"><div><dt><spring:message code="gl.code"/></dt><dd><c:out value="${transaction.code()}"/></dd></div><div><dt><spring:message code="common.name"/></dt><dd><c:out value="${transaction.name()}"/></dd></div><div><dt><spring:message code="config.event"/></dt><dd><spring:message code="gl.event.${transaction.event()}"/></dd></div></dl></div></section>
<c:url var="saveUrl" value="/accounting/config/transactions/${transaction.id()}/template"/><form:form modelAttribute="templateForm" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><form:hidden path="version"/><form:hidden path="requestKey"/>
<section class="erp-resource-section"><h2><spring:message code="config.template"/></h2><div class="fcms-section-body"><%@ include file="/WEB-INF/jsp/accounting/config/template-fields.jspf" %></div></section>
<form:errors path="" cssClass="fcms-alert" element="div" role="alert"/><div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/accounting/config/transactions/${transaction.id()}'/>"><spring:message code="common.cancel"/></a><button type="submit" class="app-btn btn-launch"><spring:message code="common.save"/></button></div></form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
