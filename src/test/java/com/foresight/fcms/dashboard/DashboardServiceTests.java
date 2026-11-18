package com.foresight.fcms.dashboard;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.foresight.fcms.auth.service.AccessService;
import com.foresight.fcms.client.service.ClientRevenueService;
import com.foresight.fcms.accounting.service.AccountingSummaryService;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.reference.FinancialYearRepository;
import com.foresight.fcms.workforce.service.WorkService;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class DashboardServiceTests {
    @Test
    void missingYearShowsUnavailablePeriodFiguresWithoutQueryingAllYearsOrInventingZeros() {
        var access = mock(AccessService.class);
        var work = mock(WorkService.class);
        var clients = mock(ClientRevenueService.class);
        var years = mock(FinancialYearRepository.class);
        var ledger = mock(LedgerService.class);
        var accounting = mock(AccountingSummaryService.class);
        when(access.require("dashboard.read"))
                .thenReturn(
                        new AccessService.Actor(
                                9,
                                Set.of(
                                        "dashboard.read",
                                        "workspace.accounting",
                                        "accounting.report",
                                        "finance.read",
                                        "client.read",
                                        "expense.read",
                                        "report.read")));
        when(years.findTop1001ByOrderByFyearDesc()).thenReturn(List.of());
        when(ledger.balance(LedgerKind.PETTY))
                .thenReturn(new LedgerService.Balance(new BigDecimal("25.01"), 0));
        var service = new DashboardService(access, work, clients, years, ledger, accounting);
        var view = service.overview(null);
        assertNull(view.selected());
        assertTrue(view.portfolios().isEmpty());
        assertFalse(view.cloudBilling());
        for (var metric : view.money()) {
            if (metric.key().equals("petty")) assertEquals(new BigDecimal("25.01"), metric.value());
            else {
                assertNull(metric.value());
                assertNull(metric.href());
            }
        }
        verifyNoInteractions(clients, accounting, work);
        verify(ledger).balance(LedgerKind.PETTY);
        verifyNoMoreInteractions(ledger);
    }
}
