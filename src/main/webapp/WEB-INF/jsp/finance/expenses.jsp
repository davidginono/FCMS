<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<div class="mb-6 flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
    <div>
        <h1 class="text-2xl font-bold text-fcms-ink">Expenses</h1>
        <p class="mt-1 text-sm text-fcms-muted">${totalExpenses} records from the migrated finance repository.</p>
    </div>
    <button class="fcms-button-primary" type="button">Add Expense</button>
</div>

<section class="fcms-panel overflow-hidden">
    <div class="overflow-x-auto">
        <table class="fcms-table">
            <thead>
            <tr>
                <th>Date</th>
                <th>Category</th>
                <th>Description</th>
                <th>Payee</th>
                <th>Amount</th>
                <th>Year</th>
                <th>Status</th>
            </tr>
            </thead>
            <tbody class="divide-y divide-slate-100">
            <c:forEach items="${expenses}" var="expense">
                <tr>
                    <td>${expense.date()}</td>
                    <td class="font-semibold text-fcms-ink">${expense.category()}</td>
                    <td>${expense.description()}</td>
                    <td>${expense.payee()}</td>
                    <td>${expense.amount()}</td>
                    <td>${expense.financialYear()}</td>
                    <td>${expense.status()}</td>
                </tr>
            </c:forEach>
            <c:if test="${empty expenses}">
                <tr>
                    <td colspan="7" class="px-4 py-10 text-center text-sm text-fcms-muted">
                        No expenses have been imported yet.
                    </td>
                </tr>
            </c:if>
            </tbody>
        </table>
    </div>
</section>

<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
