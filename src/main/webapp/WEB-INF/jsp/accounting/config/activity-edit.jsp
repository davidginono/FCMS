<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<link rel="stylesheet" href="<c:url value='/css/accounting-entry.css?v=20261118-split2'/>">
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="config.activity.edit"/></h1></div>
<c:url var="saveUrl" value="/accounting/config/activities/${activity.id()}"/>
<form:form modelAttribute="activityForm" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true"><input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"><form:hidden path="version"/>
<section class="erp-resource-section"><h2><spring:message code="common.details"/></h2><div class="fcms-section-body"><c:set var="activityEdit" value="true"/><%@ include file="/WEB-INF/jsp/accounting/config/activity-fields.jspf" %></div></section>
<form:errors path="" cssClass="fcms-alert" element="div" role="alert"/><div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='/accounting/config/activities/${activity.id()}'/>"><spring:message code="common.cancel"/></a><button class="app-btn btn-launch" type="submit"><spring:message code="common.save"/></button></div></form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
