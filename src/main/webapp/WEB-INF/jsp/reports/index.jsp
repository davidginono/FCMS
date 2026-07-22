<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ include file="/WEB-INF/jsp/shared/header.jspf" %>

<div class="mb-6">
    <h1 class="text-2xl font-bold text-fcms-ink">Reports</h1>
    <p class="mt-1 text-sm text-fcms-muted">Report workflows will replace the legacy FPDF/TCPDF print scripts in a later migration slice.</p>
</div>

<section class="grid gap-4 lg:grid-cols-3">
    <div class="fcms-panel p-5">
        <h2 class="font-bold">Client Reports</h2>
        <p class="mt-2 text-sm leading-6 text-fcms-muted">SACCOS, AMCOS, unions, microfinance, and other clients.</p>
    </div>
    <div class="fcms-panel p-5">
        <h2 class="font-bold">Finance Reports</h2>
        <p class="mt-2 text-sm leading-6 text-fcms-muted">Income, expenses, petty cash, cloud backup, and bulk SMS.</p>
    </div>
    <div class="fcms-panel p-5">
        <h2 class="font-bold">Staff Reports</h2>
        <p class="mt-2 text-sm leading-6 text-fcms-muted">Tasks, leave requests, staff reports, and approval states.</p>
    </div>
</section>

<%@ include file="/WEB-INF/jsp/shared/footer.jspf" %>
