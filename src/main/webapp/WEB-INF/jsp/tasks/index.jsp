<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<div class="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
    <div>
        <h1 class="text-2xl font-bold text-fcms-ink">Tasks</h1>
        <p class="mt-1 text-sm text-fcms-muted">${totalTasks} records from the migrated task repository.</p>
    </div>
    <button class="fcms-button-primary" type="button">Add Task</button>
</div>

<section class="fcms-panel overflow-hidden">
    <div class="overflow-x-auto">
        <table class="fcms-table">
            <thead>
            <tr>
                <th>Assignee</th>
                <th>Client</th>
                <th>Type</th>
                <th>Status</th>
                <th>Start</th>
                <th>End</th>
                <th>Activities</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
            <c:forEach items="${tasks}" var="task">
                <tr>
                    <td class="font-semibold text-fcms-ink">${task.assignee()}</td>
                    <td>${task.clientName()}</td>
                    <td>${task.taskType()}</td>
                    <td>${task.status()}</td>
                    <td>${task.startDate()}</td>
                    <td>${task.endDate()}</td>
                    <td>${task.activities()}</td>
                </tr>
            </c:forEach>
            <c:if test="${empty tasks}">
                <tr>
                    <td colspan="7" class="px-4 py-10 text-center text-sm text-fcms-muted">
                        No tasks have been imported yet.
                    </td>
                </tr>
            </c:if>
            </tbody>
        </table>
    </div>
</section>

<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
