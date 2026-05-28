package com.waitlist.admin.service;

import com.waitlist.admin.entity.Status;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class StateMachineGuardTest {

    private final StateMachineGuard guard = new StateMachineGuard();

    // ── Valid transitions ─────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} -> {1} is valid")
    @CsvSource({
            "PENDING,  APPROVED",
            "PENDING,  REJECTED",
            "APPROVED, INVITED",
            "APPROVED, REJECTED",
            "REJECTED, PENDING",
    })
    void validTransitions(Status from, Status to) {
        assertThat(guard.isValidTransition(from, to)).isTrue();
    }

    // ── Invalid transitions ───────────────────────────────────────────────────

    @ParameterizedTest(name = "{0} -> {1} is invalid")
    @CsvSource({
            "PENDING,  INVITED",   // must go through APPROVED first
            "APPROVED, PENDING",   // no going back to pending from approved
            "REJECTED, APPROVED",  // must re-enter via PENDING
            "REJECTED, INVITED",   // cannot jump to INVITED from REJECTED
            "INVITED,  PENDING",   // INVITED is terminal
            "INVITED,  APPROVED",  // terminal state
            "INVITED,  REJECTED",  // terminal state
    })
    void invalidTransitions(Status from, Status to) {
        assertThat(guard.isValidTransition(from, to)).isFalse();
    }

    // ── INVITED is truly terminal ─────────────────────────────────────────────

    @ParameterizedTest(name = "INVITED -> {0} is always invalid")
    @CsvSource({"PENDING", "APPROVED", "REJECTED", "INVITED"})
    void invitedIsTerminal(Status to) {
        assertThat(guard.isValidTransition(Status.INVITED, to)).isFalse();
    }
}
