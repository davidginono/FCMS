<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<div class="mb-6">
    <h1 class="text-2xl font-bold text-fcms-ink">Dashboard</h1>
    <p class="mt-1 text-sm text-fcms-muted">First migrated Spring MVC screen backed by PostgreSQL repositories.</p>
</div>

<section class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
    <div class="fcms-panel p-5">
        <div class="text-sm font-medium text-fcms-muted">Users</div>
        <div class="mt-3 text-3xl font-bold">${metrics.users()}</div>
    </div>
    <div class="fcms-panel p-5">
        <div class="text-sm font-medium text-fcms-muted">SACCOS</div>
        <div class="mt-3 text-3xl font-bold">${metrics.saccos()}</div>
    </div>
    <div class="fcms-panel p-5">
        <div class="text-sm font-medium text-fcms-muted">Tasks</div>
        <div class="mt-3 text-3xl font-bold">${metrics.tasks()}</div>
    </div>
    <div class="fcms-panel p-5">
        <div class="text-sm font-medium text-fcms-muted">Expenses</div>
        <div class="mt-3 text-3xl font-bold">${metrics.expenses()}</div>
    </div>
</section>

<section class="mt-6 fcms-panel p-6">
    <h2 class="text-lg font-bold">Migration Workspace</h2>
    <p class="mt-2 max-w-3xl text-sm leading-6 text-fcms-muted">
        This screen confirms the Spring Boot application shell, security configuration, JSP rendering, and repository layer are connected.
        The next migration slices should replace each PHP workflow with service-backed Spring MVC forms.
    </p>
    <div class="mt-5 flex flex-wrap gap-3">
        <a class="fcms-button-primary" href="<c:url value='/clients/saccos' />">View SACCOS</a>
        <a class="rounded-md border border-fcms-line px-4 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50" href="<c:url value='/tasks' />">View Tasks</a>
    </div>
</section>

<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
