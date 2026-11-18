<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="${kind.title}" />
</h1>
</div>
<c:url var="saveUrl" value="${formAction}" />
<form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-create="${formAction == baseUrl}" data-resource-form-page="true">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<form:hidden path="version"/>
<form:hidden path="requestKey"/>
<section class="erp-resource-section" aria-labelledby="work-form-section-1">
<h2 id="work-form-section-1">
<spring:message code="common.details" />
</h2><div class="fcms-section-body">
<div class="fcms-field">
<label for="subject">
<spring:message code="work.subject" />
</label>
<form:input id="subject" path="subject" type="text" />
<form:errors path="subject" cssClass="fcms-error" />
</div>
<c:if test="${kind.name()=='TASK' or (kind.name()=='REPORT' and permissions.contains('staff-report.assign'))}">
<div class="fcms-field">
<label for="ownerId">
<spring:message code="common.staff" />
</label>
<form:select id="ownerId" path="ownerId" required="required" data-choice-url="${pageContext.request.contextPath}/lookups/staff"><form:option value=""><spring:message code="select.select"/></form:option>
<c:forEach items="${staff}" var="option">
<form:option value="${option.id}">
<c:out value="${option.name}" />
</form:option>
</c:forEach>
</form:select>
<form:errors path="ownerId" cssClass="fcms-error" />
</div>
</c:if>
<c:if test="${kind.name()!='TASK' and not (kind.name()=='REPORT' and permissions.contains('staff-report.assign'))}"><form:hidden path="ownerId"/></c:if>
<c:if test="${kind.name()!='LEAVE'}">
<c:choose><c:when test="${permissions.contains('client.read') or permissions.contains('client.lookup')}">
<div class="fcms-field">
<label for="clientId">
<spring:message code="common.client" />
</label>
<form:select id="clientId" path="clientId" data-choice-url="${pageContext.request.contextPath}/lookups/clients">
<form:option value="">
<spring:message code="common.all" />
</form:option>
<c:forEach items="${clients}" var="option">
<form:option value="${option.id}">
<c:out value="${option.name}" />
</form:option>
</c:forEach>
</form:select>
<form:errors path="clientId" cssClass="fcms-error" />
</div>
</c:when><c:otherwise><form:hidden path="clientId"/></c:otherwise></c:choose>
<c:choose>
<c:when test="${kind.name()=='REPORT'}">
<div class="fcms-field">
<label for="workType">
<spring:message code="work.type" />
</label>
<form:select id="workType" path="workType">
<form:option value="INDIVIDUAL">
<spring:message code="work.individual" />
</form:option>
<form:option value="GROUP">
<spring:message code="work.group" />
</form:option>
</form:select>
<form:errors path="workType" cssClass="fcms-error" />
</div>
<div class="fcms-field" data-team-picker data-team-owner="${form.ownerId}">
<label for="participantChoice"><spring:message code="common.staff"/></label>
<div class="fcms-choice-row"><select id="participantChoice" data-choice-url="${pageContext.request.contextPath}/lookups/staff"><option value=""></option>
<c:forEach items="${staff}" var="option"><option value="${option.id()}"><c:out value="${option.name()}"/></option></c:forEach>
</select><button type="button" class="app-btn btn-neutral" data-team-add><spring:message code="common.add"/></button></div>
<div class="fcms-checks" data-team-members><c:forEach items="${selectedStaff}" var="person"><label><form:checkbox path="participants" value="${person.id()}"/><c:out value="${person.name()}"/></label></c:forEach></div>
<form:errors path="participants" cssClass="fcms-error"/>
</div>
</c:when>
<c:otherwise>
<div class="fcms-field">
<label for="workType">
<spring:message code="work.type" />
</label>
<form:input id="workType" path="workType" type="text" />
<form:errors path="workType" cssClass="fcms-error" />
</div>
</c:otherwise>
</c:choose>
</c:if>
<div class="fcms-field">
<label for="startAt">
<spring:message code="work.start" />
</label>
<form:input id="startAt" path="startAt" type="datetime-local" />
<form:errors path="startAt" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="endAt">
<spring:message code="work.end" />
</label>
<form:input id="endAt" path="endAt" type="datetime-local" />
<form:errors path="endAt" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="description">
<spring:message code="common.description" />
</label>
<form:textarea id="description" path="description" />
<form:errors path="description" cssClass="fcms-error" />
</div>
<c:if test="${kind.name()=='REPORT'}">
<div class="fcms-field">
<label for="outcome">
<spring:message code="work.outcome" />
</label>
<form:select id="outcome" path="outcome">
<form:option value="COMPLETED">
<spring:message code="status.COMPLETED" text="COMPLETED"/>
</form:option>
<form:option value="IN_PROGRESS">
<spring:message code="status.IN_PROGRESS" text="IN_PROGRESS"/>
</form:option>
<form:option value="INCOMPLETE">
<spring:message code="status.INCOMPLETE" text="INCOMPLETE"/>
</form:option>
</form:select>
<form:errors path="outcome" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="explanation">
<spring:message code="work.explanation" />
</label>
<form:textarea id="explanation" path="explanation" />
<form:errors path="explanation" cssClass="fcms-error" />
</div>
</c:if>
<c:if test="${kind.name()=='TASK'}">
<div class="fcms-field">
<label for="allowance">
<spring:message code="work.allowance" />
</label>
<form:input id="allowance" path="allowance" type="number" step="0.01" min="0" />
<form:errors path="allowance" cssClass="fcms-error" />
</div>
<div class="fcms-field">
<label for="amount">
<spring:message code="common.amount" />
</label>
<form:input id="amount" path="amount" type="number" step="0.01" min="0" />
<form:errors path="amount" cssClass="fcms-error" />
</div>
</c:if>
</div></section>
<form:errors path="" element="div" cssClass="fcms-alert" role="alert" />
<form:errors path="version" element="div" cssClass="fcms-alert" role="alert"/><form:errors path="requestKey" element="div" cssClass="fcms-alert" role="alert"/><div class="fcms-form-actions">
<a class="app-btn btn-neutral" href="<c:url value='${baseUrl}' />">
<spring:message code="common.cancel" />
</a>
<button class="app-btn btn-launch" type="submit">
<spring:message code="${formAction == baseUrl ? 'common.create' : 'common.save'}" />
</button>
</div>
</form:form>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
