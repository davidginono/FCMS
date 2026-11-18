<%@ tag pageEncoding="UTF-8" body-content="empty" %>
<%@ attribute name="type" required="true" %>
<%@ attribute name="messageKey" required="true" %>
<%@ attribute name="resourceUrl" required="false" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<div class="console-message" data-resource-notice data-message-type="<c:out value='${type}'/>" role="${type=='error'?'alert':'status'}" tabindex="-1">
 <div class="console-message__content"><span><spring:message code="${messageKey}" htmlEscape="true"/></span>
  <c:if test="${not empty resourceUrl}"><c:url var="noticeResourceUrl" value="${resourceUrl}"/><a href="<c:out value='${noticeResourceUrl}'/>"><spring:message code="feedback.viewResource"/></a></c:if>
 </div>
 <button type="button" data-dismiss-notice aria-label="<spring:message code='feedback.dismiss' htmlEscape='true'/>"><svg viewBox="0 0 16 16" aria-hidden="true" focusable="false"><path d="M2 2L14 14M14 2L2 14"/></svg></button>
</div>
