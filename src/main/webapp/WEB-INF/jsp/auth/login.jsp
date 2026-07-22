<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>FCMS Login</title>
    <link rel="stylesheet" href="<c:url value='/css/app.css' />">
</head>
<body class="min-h-screen bg-fcms-bg text-fcms-ink">
<main class="flex min-h-screen items-center justify-center px-4 py-10">
    <section class="w-full max-w-md rounded-lg border border-fcms-line bg-white p-8 shadow-sm">
        <div class="mb-8 text-center">
            <img src="<c:url value='/img/app-logo.png' />" alt="FCMS" class="mx-auto mb-4 h-14 w-14 rounded object-cover">
            <h1 class="text-2xl font-bold">Welcome to FCMS</h1>
            <p class="mt-2 text-sm text-fcms-muted">Sign in to continue the Spring migration workspace.</p>
        </div>

        <c:if test="${param.error != null}">
            <div class="mb-4 rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
                Wrong password, inactive account, or unknown email.
            </div>
        </c:if>
        <c:if test="${param.logout != null}">
            <div class="mb-4 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700">
                You have been logged out.
            </div>
        </c:if>

        <form method="post" action="<c:url value='/login' />" class="space-y-5">
            <input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}">
            <div>
                <label for="email" class="block text-sm font-semibold text-slate-700">Email address</label>
                <input id="email" name="email" type="email" required autocomplete="email"
                       class="mt-2 block w-full rounded-md border border-fcms-line px-3 py-2.5 text-sm outline-none focus:border-fcms-brand focus:ring-2 focus:ring-blue-100">
            </div>
            <div>
                <label for="password" class="block text-sm font-semibold text-slate-700">Password</label>
                <input id="password" name="password" type="password" required autocomplete="current-password"
                       class="mt-2 block w-full rounded-md border border-fcms-line px-3 py-2.5 text-sm outline-none focus:border-fcms-brand focus:ring-2 focus:ring-blue-100">
            </div>
            <button type="submit" class="fcms-button-primary w-full">Login</button>
        </form>
    </section>
</main>
</body>
</html>
