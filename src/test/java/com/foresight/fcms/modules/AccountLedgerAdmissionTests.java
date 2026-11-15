package com.foresight.fcms.modules;

import static com.foresight.fcms.shared.time.ApplicationCalendar.today;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.foresight.fcms.accounting.controller.FinancialStatementsController;
import com.foresight.fcms.accounting.dto.*;
import com.foresight.fcms.accounting.service.*;
import com.foresight.fcms.auth.service.*;

import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

class AccountLedgerAdmissionTests {
    JdbcTemplate db;
    AccessService access;
    AccountingService accounts;
    SourceCoverageService coverage;
    AuthRateLimitService limits;
    StatementService service;

    @BeforeEach
    void setup() {
        db = mock(JdbcTemplate.class);
        access = mock(AccessService.class);
        accounts = mock(AccountingService.class);
        coverage = mock(SourceCoverageService.class);
        limits = mock(AuthRateLimitService.class);
        service =
                new StatementService(
                        db,
                        access,
                        accounts,
                        coverage,
                        limits,
                        mock(com.foresight.fcms.accounting.service.EquityReportingService.class));
        when(access.require("accounting.report"))
                .thenReturn(new AccessService.Actor(42, Set.of("accounting.report")));
        when(accounts.books())
                .thenReturn(
                        Optional.of(
                                new AccountingViews.Book(
                                        today().withDayOfYear(1),
                                        "Verified",
                                        "ZERO",
                                        "READY",
                                        1,
                                        2L,
                                        1,
                                        true)));
        when(accounts.account(1))
                .thenReturn(
                        new AccountingViews.Account(
                                1, "111001", "Bank", "", "ASSET", "DEBIT", 4, 2L, "Bank", true,
                                true, 0));
    }

    LedgerQuery query() {
        var q = new LedgerQuery();
        q.setFrom(today().withDayOfYear(1));
        q.setTo(today());
        q.setSize(10);
        q.setPage(12);
        return q;
    }

    @Test
    void exportLimitRejectsBeforeFinancialQueriesAndUsesAuthenticatedActor() {
        assertThrows(
                StatementService.ReportLimitException.class,
                () -> service.ledgerExport(1, query()));
        verify(limits).allow("gl-ledger-export:42", 60);
        verifyNoInteractions(db, coverage);
        when(access.require("accounting.report"))
                .thenReturn(new AccessService.Actor(84, Set.of("accounting.report")));
        assertThrows(
                StatementService.ReportLimitException.class,
                () -> service.ledgerExport(1, query()));
        verify(limits).allow("gl-ledger-export:84", 60);
        verifyNoInteractions(db, coverage);
    }

    @Test
    void oversizedExportRejectsBeforeFetchingRowsAndPreservesQuery() {
        when(limits.allow(anyString(), anyInt())).thenReturn(true);
        when(db.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(
                        Map.of(
                                "opening",
                                BigDecimal.ZERO,
                                "debit",
                                BigDecimal.ZERO,
                                "credit",
                                BigDecimal.ZERO,
                                "closing",
                                BigDecimal.ZERO));
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(5001L);
        var q = query();
        var failure =
                assertThrows(IllegalArgumentException.class, () -> service.ledgerExport(1, q));
        assertEquals("error.narrowReport", failure.getMessage());
        assertEquals(12, q.getPage());
        assertEquals(10, q.getSize());
        verify(db).queryForMap(anyString(), any(Object[].class));
        verify(db).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        verifyNoMoreInteractions(db);
        verifyNoInteractions(coverage);
        clearInvocations(db, coverage);
        when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(100011L);
        q.setPage(10000);
        assertThrows(StatementService.LedgerScopeLimitException.class, () -> service.ledger(1, q));
        verify(db).queryForMap(anyString(), any(Object[].class));
        verify(db).queryForObject(anyString(), eq(Long.class), any(Object[].class));
        verifyNoMoreInteractions(db);
        verifyNoInteractions(coverage);
    }

    @Test
    void missingClaimCannotConsumeAnExportSlotOrReadTheBook() {
        when(access.require("accounting.report"))
                .thenThrow(new AccessDeniedException("Access denied"));
        assertThrows(AccessDeniedException.class, () -> service.ledgerExport(1, query()));
        verifyNoInteractions(db, accounts, coverage, limits);
    }

    @Test
    void notesExportRejectsBeforeQueriesAndKeysTheLimitToTheActor() {
        assertThrows(
                StatementService.ReportLimitException.class,
                () -> service.notesExport(query().getFrom(), query().getTo()));
        verify(limits).allow("gl-notes-export:42", 60);
        verifyNoInteractions(db, accounts, coverage);
        when(access.require("accounting.report"))
                .thenReturn(new AccessService.Actor(84, Set.of("accounting.report")));
        assertThrows(
                StatementService.ReportLimitException.class,
                () -> service.notesExport(query().getFrom(), query().getTo()));
        verify(limits).allow("gl-notes-export:84", 60);
        verifyNoInteractions(db, accounts, coverage);
    }

    @Test
    void missingNotesClaimCannotReadFinancialDataOrConsumeAnExportSlot() {
        when(access.require("accounting.report"))
                .thenThrow(new AccessDeniedException("Access denied"));
        assertThrows(
                AccessDeniedException.class,
                () -> service.notesExport(query().getFrom(), query().getTo()));
        verifyNoInteractions(db, accounts, coverage, limits);
    }

    @Test
    void throttledNotesPrintAndPdfReturn429WithoutRendering() throws Exception {
        var reports = mock(StatementService.class);
        var presentation = mock(StatementPresentation.class);
        var pdf = mock(AccountingDocumentPdfService.class);
        when(reports.notesExport(any(), any()))
                .thenThrow(new StatementService.ReportLimitException());
        var mvc =
                MockMvcBuilders.standaloneSetup(
                                new FinancialStatementsController(
                                        accounts, reports, presentation, pdf))
                        .build();
        for (String suffix : List.of("/print", "/print.pdf"))
            mvc.perform(get("/accounting/statements/notes" + suffix))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "900"))
                    .andExpect(view().name("shared/error"))
                    .andExpect(model().attribute("errorCode", "error.exportBusy"));
        verifyNoInteractions(presentation, pdf);
        verify(accounts, times(2)).books();
        verifyNoMoreInteractions(accounts);
    }

    @Test
    void throttledExportReturns429WithRetryHeaderAndLocalizedErrorKey() throws Exception {
        var reports = mock(StatementService.class);
        when(reports.ledgerExport(eq(1L), any()))
                .thenThrow(new StatementService.ReportLimitException());
        when(reports.ledger(eq(1L), any())).thenThrow(new StatementService.ReportLimitException());
        var controller =
                new FinancialStatementsController(
                        accounts,
                        reports,
                        mock(StatementPresentation.class),
                        mock(AccountingDocumentPdfService.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String suffix : List.of("", "/print", "/print.pdf"))
            mvc.perform(get("/accounting/accounts/1/ledger" + suffix))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "900"))
                    .andExpect(view().name("shared/error"))
                    .andExpect(model().attribute("errorCode", "error.exportBusy"));
    }

    @Test
    void previewLimitRejectsBeforeFinancialQueries() {
        assertThrows(StatementService.ReportLimitException.class, () -> service.ledger(1, query()));
        verify(limits).allow("gl-ledger-read:42", 600);
        verifyNoInteractions(db, coverage);
    }

    @Test
    void oversizedScopeReturnsActionableErrorInsteadOfPartialLedger() throws Exception {
        var reports = mock(StatementService.class);
        when(reports.ledgerExport(eq(1L), any()))
                .thenThrow(new StatementService.LedgerScopeLimitException());
        when(reports.ledger(eq(1L), any()))
                .thenThrow(new StatementService.LedgerScopeLimitException());
        var controller =
                new FinancialStatementsController(
                        accounts,
                        reports,
                        mock(StatementPresentation.class),
                        mock(AccountingDocumentPdfService.class));
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String suffix : List.of("", "/print", "/print.pdf"))
            mvc.perform(get("/accounting/accounts/1/ledger" + suffix))
                    .andExpect(status().isBadRequest())
                    .andExpect(view().name("shared/error"))
                    .andExpect(model().attribute("errorCode", "error.narrowReport"));
    }
}
