<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<c:out value="${record.subject()}" />
</h1>
<c:if test="${(record.status()=='DRAFT' || record.status()=='REJECTED' || record.status()=='PLANNED' || record.status()=='IN_PROGRESS') && (record.ownerId()==actorId || (kind.name()=='TASK' && permissions.contains('task.manage')))}">
<a class="app-btn btn-neutral" href="<c:url value='${baseUrl}/${record.id()}/edit'/>">
<spring:message code="common.edit" />
</a>
</c:if>
</div>
<dl class="fcms-details">
<div>
<dt>
<spring:message code="common.staff" />
</dt>
<dd>
<c:out value="${record.owner()}" />
</dd>
</div>
<c:if test="${kind.name()!='LEAVE'}"><div>
<dt>
<spring:message code="common.client" />
</dt>
<dd>
<c:out value="${record.client()}" />
</dd>
</div></c:if>
<div>
<dt>
<spring:message code="work.start" />
</dt>
<dd>
<c:out value="${appTime:display(record.startAt())}" />
</dd>
</div>
<div>
<dt>
<spring:message code="work.end" />
</dt>
<dd>
<c:out value="${appTime:display(record.endAt())}" />
</dd>
</div>
<div>
<dt>
<spring:message code="common.description" />
</dt>
<dd>
<c:out value="${record.description()}" />
</dd>
</div>
<c:if test="${kind.name()!='LEAVE'}"><div>
<dt>
<spring:message code="work.type" />
</dt>
<dd>
<c:out value="${record.workType()}" />
</dd>
</div></c:if>
<c:if test="${kind.name()=='REPORT'}"><div>
<dt>
<spring:message code="work.outcome" />
</dt>
<dd>
<c:out value="${record.outcome()}" />
</dd>
</div></c:if>
<c:if test="${kind.name()=='REPORT'}"><div>
<dt>
<spring:message code="work.explanation" />
</dt>
<dd>
<c:out value="${record.explanation()}" />
</dd>
</div></c:if>
<c:if test="${kind.name()=='TASK'}"><div>
<dt>
<spring:message code="work.allowance" />
</dt>
<dd>
<c:out value="${record.allowance()}" />
</dd>
</div></c:if>
<c:if test="${kind.name()=='TASK'}"><div>
<dt>
<spring:message code="common.amount" />
</dt>
<dd>
<c:out value="${record.amount()}" />
</dd>
</div></c:if>
<div>
<dt>
<spring:message code="common.status" />
</dt>
<dd>
<spring:message code="status.${record.status()}" text="${record.status()}"/>
</dd>
</div>
<div>
<dt>
<spring:message code="common.comment" />
</dt>
<dd>
<c:out value="${record.reviewComment()}" />
</dd>
</div>
<c:if test="${not empty participants}">
<div>
<dt>
<spring:message code="common.staff" />
</dt>
<dd>
<c:forEach items="${participants}" var="participant">
<div>
<c:out value="${participant}"/>
</div>
</c:forEach>
</dd>
</div>
</c:if>
</dl>
<div class="app-actions">
<c:if test="${kind.name()=='TASK' && (record.ownerId()==actorId || permissions.contains('task.manage')) && record.status()=='PLANNED'}">
<form method="post" action="<c:url value='${baseUrl}/${record.id()}/actions/start'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<button class="app-btn btn-neutral" type="submit">
<spring:message code="work.startAction" />
</button>
</form>
</c:if>
<c:if test="${kind.name()=='TASK' && (record.ownerId()==actorId || permissions.contains('task.manage')) && (record.status()=='PLANNED' || record.status()=='IN_PROGRESS')}">
<form method="post" action="<c:url value='${baseUrl}/${record.id()}/actions/complete'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<button class="app-btn btn-neutral" type="submit">
<spring:message code="work.complete" />
</button>
</form>
</c:if>
<c:if test="${kind.name()=='TASK' && permissions.contains('task.manage') && (record.status()=='PLANNED' || record.status()=='IN_PROGRESS')}">
<form method="post" action="<c:url value='${baseUrl}/${record.id()}/actions/cancel'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<button class="app-btn btn-neutral" type="submit">
<spring:message code="work.cancel" />
</button>
</form>
</c:if>
<c:if test="${kind.name()!='TASK' && record.ownerId()==actorId && (record.status()=='DRAFT' || record.status()=='REJECTED')}">
<form method="post" action="<c:url value='${baseUrl}/${record.id()}/actions/submit'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<button class="app-btn btn-neutral" type="submit">
<spring:message code="common.submit" />
</button>
</form>
</c:if>
</div>
<c:if test="${record.status()=='SUBMITTED' && record.ownerId()!=actorId && permissions.contains(kind.claim.concat('.review'))}">
<fieldset class="fcms-form-section">
<legend>
<spring:message code="work.review" />
</legend>
<form method="post" action="<c:url value='${baseUrl}/${record.id()}/actions/approve'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<div class="fcms-field">
<label for="reviewComment">
<spring:message code="common.comment" />
</label>
<textarea id="reviewComment" name="comment" maxlength="1000" required>
</textarea>
</div>
<div class="fcms-form-actions">
<button class="app-btn btn-neutral" type="submit">
<spring:message code="common.approve" />
</button>
<button class="app-btn btn-neutral" type="submit" formaction="<c:url value='${baseUrl}/${record.id()}/actions/reject'/>">
<spring:message code="common.reject" />
</button>
</div>
</form>
</fieldset>
</c:if>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
