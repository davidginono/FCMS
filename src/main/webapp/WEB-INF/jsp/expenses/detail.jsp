<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<c:if test="${not empty actionError}"><div class="fcms-alert" role="alert"><spring:message code="error.conflict"/></div></c:if><div class="app-page-header">
<h1 class="app-page-title">
<spring:message code="nav.expenses" />
</h1>
</div>
<dl class="fcms-details">
<div>
<dt>
<spring:message code="common.date" />
</dt>
<dd>
<c:out value="${appTime:display(record.date())}" />
</dd>
</div>
<div>
<dt>
<spring:message code="ledger.category" />
</dt>
<dd>
<c:out value="${record.category()}" />
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
<div>
<dt>
<spring:message code="expense.payee" />
</dt>
<dd>
<c:out value="${record.payee()}" />
</dd>
</div>
<div>
<dt>
<spring:message code="common.amount" />
</dt>
<dd>
<c:out value="${record.amount()}" />
</dd>
</div>
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
<spring:message code="common.staff" />
</dt>
<dd>
<c:out value="${record.owner()}" />
</dd>
</div>
<div>
<dt>
<spring:message code="common.comment" />
</dt>
<dd>
<c:out value="${record.comment()}" />
</dd>
</div>
<div>
<dt>
<spring:message code="expense.paymentReference" />
</dt>
<dd>
<c:out value="${record.paymentReference()}" />
</dd>
</div>
<div><dt><spring:message code="report.year"/></dt><dd><c:out value="${record.year()}"/></dd></div>
<div><dt><spring:message code="work.createdBy"/></dt><dd><c:choose><c:when test="${not empty record.creator()}"><c:out value="${record.creator()}"/></c:when><c:otherwise><spring:message code="gl.presentation.unavailable"/></c:otherwise></c:choose></dd></div>
<div><dt><spring:message code="work.preparedBy"/></dt><dd><c:choose><c:when test="${not empty record.preparer()}"><c:out value="${record.preparer()}"/></c:when><c:otherwise><spring:message code="gl.presentation.unavailable"/></c:otherwise></c:choose></dd></div>
</dl>
<c:if test="${paymentEvidenceAvailable and (permissions.contains('expense.evidence.write') or permissions.contains('expense.evidence.review'))}"><div class="app-actions"><a class="app-btn btn-neutral" href="<c:url value='/finance/expenses/${record.id()}/evidence'/>"><spring:message code="expense.evidence.title"/></a></div></c:if>
<c:if test="${commands.prepare()}">
<div class="app-actions">
<a class="app-btn btn-neutral" href="<c:url value='/finance/expenses/${record.id()}/edit'/>">
<spring:message code="common.edit" />
</a>
<form method="post" action="<c:url value='/finance/expenses/${record.id()}/actions/submit'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<input type="hidden" name="requestKey" value="${requestKey}">
<button type="submit" class="app-btn btn-neutral">
<spring:message code="common.submit" />
</button>
</form>
</div>
</c:if>
<c:if test="${commands.review()}">
<details class="fcms-form-section">
<summary>
<spring:message code="common.approve" />
</summary>
<form method="post" action="<c:url value='/finance/expenses/${record.id()}/actions/approve'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<input type="hidden" name="requestKey" value="${requestKey}">
<div class="fcms-field">
<label for="approve-input">
<spring:message code="common.reason" />
</label>
<input id="approve-input" name="comment" maxlength="1000" value="<c:out value='${param.comment}'/>" required>
</div>
<button class="app-btn btn-neutral" type="submit">
<spring:message code="common.approve" />
</button>
</form>
</details>
</c:if>
<c:if test="${commands.review()}">
<details class="fcms-form-section">
<summary>
<spring:message code="common.reject" />
</summary>
<form method="post" action="<c:url value='/finance/expenses/${record.id()}/actions/reject'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<input type="hidden" name="requestKey" value="${requestKey}">
<div class="fcms-field">
<label for="reject-input">
<spring:message code="common.reason" />
</label>
<input id="reject-input" name="comment" maxlength="1000" value="<c:out value='${param.comment}'/>" required>
</div>
<button class="app-btn btn-neutral" type="submit">
<spring:message code="common.reject" />
</button>
</form>
</details>
</c:if>
<c:if test="${commands.pay()}">
<details class="fcms-form-section">
<summary>
<spring:message code="expense.paid" />
</summary>
<form method="post" action="<c:url value='/finance/expenses/${record.id()}/actions/pay'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<input type="hidden" name="requestKey" value="${requestKey}">
<div class="fcms-field">
<label for="pay-input">
<spring:message code="expense.paymentReference" />
</label>
<input id="pay-input" name="reference" maxlength="100" value="<c:out value='${param.reference}'/>" required>
</div>
<button class="app-btn btn-neutral" type="submit">
<spring:message code="expense.paid" />
</button>
</form>
</details>
</c:if>
<c:if test="${commands.reverse()}">
<details class="fcms-form-section">
<summary>
<spring:message code="ledger.confirmReverse" />
</summary>
<form method="post" action="<c:url value='/finance/expenses/${record.id()}/actions/reverse'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="version" value="${record.version()}">
<input type="hidden" name="requestKey" value="${requestKey}">
<div class="fcms-field">
<label for="reverse-input">
<spring:message code="common.reason" />
</label>
<input id="reverse-input" name="comment" maxlength="1000" value="<c:out value='${param.comment}'/>" required>
</div>
<button class="app-btn btn-neutral" type="submit">
<spring:message code="ledger.confirmReverse" />
</button>
</form>
</details>
</c:if>
<c:set var="baseUrl" value="/finance/expenses/${record.id()}"/><c:set var="resourceKey" value="expense-history"/><c:set var="records" value="${historyRecords}"/><c:set var="query" value="${historyQuery}"/><c:set var="registerStatuses" value=""/><section class="erp-table-wrap" data-resource-table="expense-history" data-table-page-size="${historyQuery.size}" data-table-page-size-options="10 25 50 100"><div class="app-table-titlebar"><h2><spring:message code="expense.history"/></h2></div><%@ include file="/WEB-INF/jsp/reusableuicomponents/register-filters.jspf" %><div class="erp-table-scroll"><table class="erp-table"><thead><tr><th data-table-sort-key="date" data-table-sort-asc="date" data-table-sort-desc="date"><spring:message code="common.date"/></th><th data-table-sort-key="actor" data-table-sort-asc="actor" data-table-sort-desc="actor"><spring:message code="ledger.recordedBy"/></th><th data-table-sort-key="from" data-table-sort-asc="from" data-table-sort-desc="from"><spring:message code="common.from"/></th><th data-table-sort-key="to" data-table-sort-asc="to" data-table-sort-desc="to"><spring:message code="common.to"/></th><th data-table-sort-key="amount" data-table-sort-asc="amount" data-table-sort-desc="amount"><spring:message code="common.amount"/></th><th data-table-sort-key="reason" data-table-sort-asc="reason" data-table-sort-desc="reason"><spring:message code="common.reason"/></th><th data-table-sort-key="reference" data-table-sort-asc="reference" data-table-sort-desc="reference"><spring:message code="expense.paymentReference"/></th></tr></thead><tbody><c:forEach items="${historyRecords.rows()}" var="event"><tr><td><c:out value="${appTime:display(event.occurredAt())}"/></td><td><c:out value="${event.actor()}"/></td><td><spring:message code="status.${event.fromStatus()}"/></td><td><spring:message code="status.${event.toStatus()}"/></td><td><c:out value="${event.amount()}"/></td><td><c:out value="${event.reason()}"/></td><td><c:out value="${event.paymentReference()}"/></td></tr></c:forEach><c:if test="${empty historyRecords.rows()}"><tr><td colspan="7"><spring:message code="common.noRecords"/></td></tr></c:if></tbody></table></div><%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %></section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
