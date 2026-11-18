<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<h1 class="app-page-title"><spring:message code="client.updateContract"/></h1>
<c:url var="contractSaveUrl" value="${clientRoute}/${record.id()}/contract"/>
<form:form modelAttribute="form" method="post" action="${contractSaveUrl}" cssClass="fcms-form" enctype="multipart/form-data" data-resource-create="true" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<form:hidden path="version"/>
<section class="erp-resource-section" aria-labelledby="contract-heading">
<h2 id="contract-heading"><c:out value="${record.name()}"/></h2>
<div class="fcms-section-body">
<c:if test="${record.hasContract()}"><a href="<c:url value='${clientRoute}/${record.id()}/contract'/>"><spring:message code="client.currentContract"/></a></c:if>
<div class="fcms-field"><label for="contractUpload"><spring:message code="client.contract"/></label>
<input type="file" id="contractUpload" name="contractUpload" accept="application/pdf,.pdf" required aria-describedby="contractUpload-errors">
<form:errors id="contractUpload-errors" path="contractUpload" cssClass="fcms-error"/>
</div></div></section>
<form:errors path="" cssClass="fcms-alert" element="div" role="alert"/>
<form:errors path="version" cssClass="fcms-alert" element="div" role="alert"/>
<div class="fcms-form-actions"><a class="app-btn btn-neutral" href="<c:url value='${clientRoute}/${record.id()}'/>"><spring:message code="common.cancel"/></a><button type="submit" class="app-btn btn-launch"><spring:message code="common.save"/></button></div>
</form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
