<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="app-page-header"><h1 class="app-page-title"><spring:message code="${kind.claim}.details"/></h1>
<div class="fcms-form-actions">
<c:if test="${permissions.contains(kind.claim.concat('.write'))}"><a class="app-btn btn-neutral" href="<c:url value='${kind.route}/${record.id()}/edit'/>"><spring:message code="common.edit"/></a></c:if>
<c:if test="${permissions.contains(kind.claim.concat('.export'))}">
<a class="app-btn btn-neutral" href="<c:url value='${kind.route}/${record.id()}/document.pdf?download=true'/>"><spring:message code="common.pdf"/></a>
<a class="app-btn btn-neutral" target="_blank" rel="noopener" href="<c:url value='${kind.route}/${record.id()}/document.pdf'/>"><spring:message code="common.print"/></a></c:if></div></div>
<section class="erp-resource-section"><h2><spring:message code="common.client"/></h2><div class="fcms-section-body"><dl class="fcms-details">
<div><dt><spring:message code="sales.clientType"/></dt><dd><spring:message code="sales.type.${record.data().clientType}"/></dd></div>
<div><dt><spring:message code="common.name"/></dt><dd><c:out value="${record.data().clientName}"/></dd></div>
<div><dt><spring:message code="sales.region"/></dt><dd><c:out value="${record.data().region}"/></dd></div>
<div><dt><spring:message code="sales.contactName"/></dt><dd><c:out value="${record.data().contactName}"/></dd></div>
<div><dt><spring:message code="sales.phone"/></dt><dd><c:out value="${record.data().phone}"/></dd></div>
<div><dt><spring:message code="common.email"/></dt><dd><c:out value="${record.data().email}"/></dd></div>
<div><dt><spring:message code="sales.billingAddress"/></dt><dd><c:out value="${record.data().billingAddress}"/></dd></div>
<div><dt><spring:message code="sales.billingCity"/></dt><dd><c:out value="${record.data().billingCity}"/></dd></div>
<div><dt><spring:message code="sales.billingTin"/></dt><dd><c:out value="${record.data().billingTin}"/></dd></div>
</dl></div></section>
<section class="erp-resource-section"><h2><spring:message code="sales.document"/></h2><div class="fcms-section-body"><dl class="fcms-details">
<div><dt><spring:message code="${kind.claim}.number"/></dt><dd><c:out value="${record.data().documentNumber}"/></dd></div>
<div><dt><spring:message code="sales.documentDate"/></dt><dd><c:out value="${appTime:date(record.data().documentDate)}"/></dd></div>
<div><dt><spring:message code="${kind.claim}.until"/></dt><dd><c:out value="${appTime:date(record.data().validUntil)}"/></dd></div>
<div><dt><spring:message code="sales.currency"/></dt><dd><c:out value="${record.data().currency}"/></dd></div>
<div><dt><spring:message code="sales.amount"/></dt><dd><fmt:formatNumber value="${record.amount()}" minFractionDigits="2" maxFractionDigits="2"/></dd></div>
<div><dt><spring:message code="sales.paymentMethod"/></dt><dd><spring:message code="sales.payment.${record.data().paymentMethod}"/></dd></div>
<div><dt><spring:message code="sales.terms"/></dt><dd class="sales-multiline"><c:out value="${record.data().terms}"/></dd></div>
</dl></div></section>
<section class="erp-table-wrap"><div class="app-table-toolbar"><h2><spring:message code="sales.items"/></h2></div><div class="erp-table-scroll"><table class="erp-table">
<thead><tr><th><spring:message code="sales.item"/></th><th><spring:message code="common.description"/></th><th><spring:message code="sales.amount"/> (<c:out value="${record.data().currency}"/>)</th></tr></thead><tbody>
<c:forEach items="${record.data().items}" var="item" varStatus="loop"><tr><td>${loop.count}</td><td class="sales-multiline"><c:out value="${item.description}"/></td><td><fmt:formatNumber value="${item.amount}" minFractionDigits="2" maxFractionDigits="2"/></td></tr></c:forEach>
</tbody></table></div></section>
<section class="erp-table-wrap" data-resource-table="${resourceKey}" data-table-page-size="${query.size}" data-table-page-size-options="10 25 50 100"><div class="app-table-titlebar"><h2><spring:message code="sales.history"/></h2></div><form method="get" action="<c:url value='${baseUrl}'/>"><label><spring:message code="common.search"/><input type="search" name="search" maxlength="100" value="<c:out value='${query.search}'/>"></label><button class="app-btn btn-neutral" type="submit"><spring:message code="common.apply"/></button><input type="hidden" name="size" value="${query.size}"><input type="hidden" name="sort" value="<c:out value='${query.sort}'/>"><input type="hidden" name="direction" value="<c:out value='${query.direction}'/>"></form><div class="erp-table-scroll"><table class="erp-table">
<thead><tr><th data-table-sort-key="revision" data-table-sort-asc="revision" data-table-sort-desc="revision"><spring:message code="sales.revision"/></th><th data-table-sort-key="actor" data-table-sort-asc="actor" data-table-sort-desc="actor"><spring:message code="sales.actor"/></th><th data-table-sort-key="at" data-table-sort-asc="at" data-table-sort-desc="at"><spring:message code="common.date"/></th></tr></thead><tbody>
<c:forEach items="${records.rows()}" var="revision"><tr><td>${revision.version()+1}</td><td>#${revision.actor()}</td><td><c:out value="${appTime:display(revision.at())}"/></td></tr></c:forEach>
<c:if test="${empty records.rows()}"><tr><td colspan="3"><spring:message code="common.noRecords"/></td></tr></c:if>
</tbody></table></div><%@ include file="/WEB-INF/jsp/reusableuicomponents/register-pager.jspf" %></section>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
