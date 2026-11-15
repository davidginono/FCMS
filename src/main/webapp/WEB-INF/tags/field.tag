<%@ tag body-content="empty" pageEncoding="UTF-8" %>
<%@ attribute name="path" required="true" %><%@ attribute name="label" required="true" %><%@ attribute name="type" required="false" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %><%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
<div class="fcms-field"><label for="${path}"><spring:message code="${label}"/></label><form:input path="${path}" id="${path}" type="${empty type?'text':type}"/><form:errors path="${path}" cssClass="fcms-error"/></div>
