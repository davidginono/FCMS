package com.foresight.fcms.modules;

import static org.junit.jupiter.api.Assertions.*;

import com.foresight.fcms.auth.service.AuthRateLimitService;
import com.foresight.fcms.finance.ledger.model.LedgerKind;
import com.foresight.fcms.finance.ledger.service.LedgerService;
import com.foresight.fcms.shared.dto.*;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

class RegisterAndLedgerTests {
    @Test
    void registerBoundsAndEscapesInput() {
        var q = new RegisterQuery();
        q.setPage(-1);
        q.setSize(Integer.MAX_VALUE);
        q.setSort("amount;drop table users");
        q.setDirection("desc;select");
        q.setSearch("a%_");
        assertEquals(0, q.getPage());
        assertEquals(25, q.getSize());
        assertEquals("id desc, id desc", q.order(Map.of("amount", "amount")));
        assertEquals("%a!%!_%", q.pattern());
    }

    @Test
    void pageUsesLookaheadAndNeverExposesIt() {
        var q = new RegisterQuery();
        q.setSize(10);
        var p = RegisterPage.of(java.util.stream.IntStream.range(0, 11).boxed().toList(), q);
        assertEquals(10, p.rows().size());
        assertTrue(p.hasNext());
        assertThrows(UnsupportedOperationException.class, () -> p.rows().add(12));
    }

    @Test
    void overdraftAndUnitsAreIndependentInvariants() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LedgerService.checkBalance(LedgerKind.PETTY, new BigDecimal("-0.01"), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> LedgerService.checkBalance(LedgerKind.BULK, BigDecimal.TEN, -1));
        assertDoesNotThrow(
                () -> LedgerService.checkBalance(LedgerKind.BULK, new BigDecimal("-100"), 10));
    }

    @Test
    void recoveryRateLimitHasAnActualBound() {
        var l = new AuthRateLimitService();
        for (int i = 0; i < 20; i++) assertTrue(l.allow("test", 20));
        assertFalse(l.allow("test", 20));
        assertTrue(l.allow("different", 20));
    }
}
