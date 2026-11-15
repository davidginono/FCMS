<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<%@ taglib prefix="ui" tagdir="/WEB-INF/tags" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="config.activity"/></h1></div>
<div class="app-actions"><a class="app-btn btn-neutral" href="<c:url value='/accounting/config/transactions'><c:param name='activityId' value='${activity.id()}'/></c:url>"><spring:message code="config.transactions"/></a><c:if test="${permissions.contains('accounting.setup')}"><a class="app-btn btn-neutral" href="<c:url value='/accounting/config/activities/${activity.id()}/edit'/>"><spring:message code="common.edit"/></a></c:if></div>
<section class="erp-resource-section"><h2><spring:message code="common.details"/></h2><div class="fcms-section-body"><dl class="fcms-details">
<div><dt><spring:message code="gl.code"/></dt><dd><c:out value="${activity.code()}"/></dd></div><div><dt><spring:message code="common.name"/></dt><dd><c:out value="${activity.name()}"/></dd></div><div><dt><spring:message code="common.description"/></dt><dd><c:out value="${activity.description()}"/></dd></div><div><dt><spring:message code="common.status"/></dt><dd><spring:message code="status.${activity.active()?'ACTIVE':'INACTIVE'}"/></dd></div><div><dt><spring:message code="config.transactions"/></dt><dd><c:out value="${activity.transactions()}"/></dd></div>
</dl></div></section>
<c:if test="${not empty actionError}"><div class="fcms-alert" role="alert"><spring:message code="${actionError}"/></div></c:if>
<c:set var="stateResource" value="${activity}"/><c:set var="stateUrl" value="/accounting/config/activities/${activity.id()}/state"/><%@ include file="/WEB-INF/jsp/accounting/config/state-form.jspf" %>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
