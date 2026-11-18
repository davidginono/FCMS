<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header">
<h1 class="app-page-title">
<c:out value="${record.reference()}" />
</h1>
<button class="app-btn btn-neutral" type="button" data-print-page>
<ui:reportExport format="print"/>
</button><a class="app-btn btn-neutral" href="<c:url value='${baseUrl}/${record.id()}/print.pdf'/>"><ui:reportExport format="pdf"/></a>
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
<spring:message code="common.reference" />
</dt>
<dd>
<c:out value="${record.reference()}" />
</dd>
</div>
<div>
<dt>
<spring:message code="ledger.category" />
</dt>
<dd>
<spring:message code="ledger.operation.${record.category()}" text="${record.category()}"/>
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
<spring:message code="ledger.paymentMode" />
</dt>
<dd>
<c:out value="${record.paymentMode()}" />
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
<c:if test="${kind.name()=='BULK'}"><div>
<dt>
<spring:message code="ledger.units" />
</dt>
<dd>
<c:out value="${record.units()}" />
</dd>
</div></c:if>
<div>
<dt>
<spring:message code="ledger.balance" />
</dt>
<dd>
<c:out value="${record.balance()}" />
</dd>
</div>
<c:if test="${kind.name()=='BULK'}"><div>
<dt>
<spring:message code="ledger.unitBalance" />
</dt>
<dd>
<c:out value="${record.unitBalance()}" />
</dd>
</div></c:if>
<div>
<dt>
<spring:message code="ledger.recordedBy" />
</dt>
<dd>
<c:out value="${record.actor()}" />
</dd>
</div>
<div>
<dt>
<spring:message code="common.client" />
</dt>
<dd>
<c:out value="${record.client()}" />
</dd>
</div>
<c:if test="${not empty record.reversalOf()}">
<div>
<dt>
<spring:message code="ledger.reversalOf" />
</dt>
<dd>
<a href="<c:url value='${baseUrl}/${record.reversalOf()}'/>">
<c:out value="${record.reversalOf()}"/>
</a>
</dd>
</div>
</c:if>
<c:if test="${not empty record.reversedBy()}">
<div>
<dt>
<spring:message code="ledger.reversedBy" />
</dt>
<dd>
<a href="<c:url value='${baseUrl}/${record.reversedBy()}'/>">
<c:out value="${record.reversedBy()}"/>
</a>
</dd>
</div>
</c:if>
</dl>
<c:if test="${permissions.contains('finance.reverse') && record.actorId()!=actorId && empty record.reversedBy() && empty record.reversalOf()}">
<details class="fcms-form-section">
<summary>
<spring:message code="ledger.confirmReverse" />
</summary>
<form method="post" action="<c:url value='${baseUrl}/${record.id()}/reverse'/>">
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
<input type="hidden" name="requestKey" value="${requestKey}">
<div class="fcms-field">
<label for="reason">
<spring:message code="ledger.reversalReason" />
</label>
<textarea id="reason" name="reason" maxlength="500" required>
</textarea>
</div>
<button class="app-btn btn-neutral" type="submit">
<spring:message code="ledger.confirmReverse" />
</button>
</form>
</details>
</c:if>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
