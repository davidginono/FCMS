<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<div class="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
    <div>
        <h1 class="text-2xl font-bold text-fcms-ink">SACCOS</h1>
        <p class="mt-1 text-sm text-fcms-muted">${totalClients} records from the migrated repository.</p>
    </div>
    <button class="fcms-button-primary" type="button">Add SACCOS</button>
</div>

<section class="fcms-panel overflow-hidden">
    <div class="overflow-x-auto">
        <table class="fcms-table">
            <thead>
            <tr>
                <th>Name</th>
                <th>Region</th>
                <th>Status</th>
                <th>Entry Date</th>
                <th>Expire Date</th>
                <th>Amount</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
            <c:forEach items="${clients}" var="client">
                <tr>
                    <td class="font-semibold text-fcms-ink">${client.name()}</td>
                    <td>${client.region()}</td>
                    <td>${client.status()}</td>
                    <td>${client.entryDate()}</td>
                    <td>${client.expireDate()}</td>
                    <td>${client.amount()}</td>
                </tr>
            </c:forEach>
            <c:if test="${empty clients}">
                <tr>
                    <td colspan="6" class="px-4 py-10 text-center text-sm text-fcms-muted">
                        No SACCOS records have been imported yet.
                    </td>
                </tr>
            </c:if>
            </tbody>
        </table>
    </div>
</section>

<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
