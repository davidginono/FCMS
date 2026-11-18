<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>
<div class="fcms-form-stack">
    <div class="app-page-header">
        <h1 class="app-page-title"><spring:message code="account.profile"/></h1>
        <div class="app-actions">
            <a class="app-btn btn-neutral" href="<c:url value='/account/password'/>"><spring:message code="account.changePassword"/></a>
        </div>
    </div>
    <c:url var="saveUrl" value="/account"/>
    <form:form modelAttribute="form" method="post" action="${saveUrl}" cssClass="fcms-form" data-resource-form-page="true">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
        <section class="erp-resource-section" aria-labelledby="profile-details-title">
            <h2 id="profile-details-title"><spring:message code="common.details"/></h2>
            <div class="fcms-section-body">
                <div class="fcms-field">
                    <label for="firstName"><spring:message code="common.firstName"/></label>
                    <form:input id="firstName" path="firstName" type="text"/>
                    <form:errors path="firstName" cssClass="fcms-error"/>
                </div>
                <div class="fcms-field">
                    <label for="lastName"><spring:message code="common.lastName"/></label>
                    <form:input id="lastName" path="lastName" type="text"/>
                    <form:errors path="lastName" cssClass="fcms-error"/>
                </div>
                <div class="fcms-field">
                    <label for="leaveEmail"><spring:message code="account.leaveEmail"/></label>
                    <form:input id="leaveEmail" path="leaveEmail" type="email"/>
                    <form:errors path="leaveEmail" cssClass="fcms-error"/>
                </div>
            </div>
        </section>
        <form:errors path="" element="div" cssClass="fcms-alert" role="alert"/>
        <div class="fcms-form-actions">
            <a class="app-btn btn-neutral" href="<c:url value='/dashboard'/>"><spring:message code="common.cancel"/></a>
            <button class="app-btn btn-launch" type="submit"><spring:message code="common.save"/></button>
        </div>
    </form:form>
    <form class="fcms-form" method="post" enctype="multipart/form-data" action="<c:url value='/account/photo'/>">
        <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
        <section class="erp-resource-section" aria-labelledby="profile-photo-title">
            <h2 id="profile-photo-title"><spring:message code="account.photo"/></h2>
            <div class="fcms-section-body">
                <c:if test="${hasPhoto}"><img class="fcms-avatar" src="<c:url value='/account/photo'/>" alt="<spring:message code='account.photo'/>"></c:if>
                <c:if test="${not empty photoError}"><div class="fcms-alert" role="alert"><spring:message code="${photoError}"/></div></c:if>
                <div class="fcms-field">
                    <label for="photo"><spring:message code="account.photo"/></label>
                    <input id="photo" type="file" name="photo" accept="image/png,image/jpeg" required>
                </div>
            </div>
        </section>
        <div class="fcms-form-actions">
            <button class="app-btn btn-launch" type="submit"><spring:message code="common.save"/></button>
        </div>
    </form>
</div>
<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
