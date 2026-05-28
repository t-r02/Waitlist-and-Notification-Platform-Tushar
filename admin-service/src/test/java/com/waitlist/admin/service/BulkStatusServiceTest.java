package com.waitlist.admin.service;

import com.waitlist.admin.dto.response.BulkStatusResponse;
import com.waitlist.admin.entity.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class BulkStatusServiceTest {

    @Mock EntryManagementService entryService;

    BulkStatusService bulkService;

    @BeforeEach
    void setUp() {
        bulkService = new BulkStatusService(entryService);
    }

    @Test
    void allSucceed_successCountEqualsListSize() {
        doNothing().when(entryService).updateStatus(1L, Status.APPROVED, "admin");
        doNothing().when(entryService).updateStatus(2L, Status.APPROVED, "admin");

        BulkStatusResponse resp = bulkService.bulkUpdate(List.of(1L, 2L), Status.APPROVED, "admin");

        assertThat(resp.getSuccessCount()).isEqualTo(2);
        assertThat(resp.getFailures()).isEmpty();
    }

    @Test
    void someFail_failuresCollectedAndSuccessCountIsPartial() {
        doNothing().when(entryService).updateStatus(1L, Status.APPROVED, "admin");
        doThrow(new NoSuchElementException("Entry not found: 2"))
                .when(entryService).updateStatus(2L, Status.APPROVED, "admin");
        doNothing().when(entryService).updateStatus(3L, Status.APPROVED, "admin");

        BulkStatusResponse resp = bulkService.bulkUpdate(List.of(1L, 2L, 3L), Status.APPROVED, "admin");

        assertThat(resp.getSuccessCount()).isEqualTo(2);
        assertThat(resp.getFailures()).hasSize(1);
        assertThat(resp.getFailures().get(0)).contains("2").contains("Entry not found: 2");
    }

    @Test
    void allFail_successCountIsZeroAndAllFailuresRecorded() {
        doThrow(new IllegalStateException("Invalid transition: INVITED -> APPROVED"))
                .when(entryService).updateStatus(10L, Status.APPROVED, "admin");
        doThrow(new NoSuchElementException("Entry not found: 11"))
                .when(entryService).updateStatus(11L, Status.APPROVED, "admin");

        BulkStatusResponse resp = bulkService.bulkUpdate(List.of(10L, 11L), Status.APPROVED, "admin");

        assertThat(resp.getSuccessCount()).isZero();
        assertThat(resp.getFailures()).hasSize(2);
    }

    @Test
    void emptyIds_successCountIsZero_noServiceCalls() {
        BulkStatusResponse resp = bulkService.bulkUpdate(List.of(), Status.APPROVED, "admin");

        assertThat(resp.getSuccessCount()).isZero();
        assertThat(resp.getFailures()).isEmpty();
        verify(entryService, org.mockito.Mockito.never()).updateStatus(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void firstItemFails_remainingItemsAreStillAttempted() {
        doThrow(new RuntimeException("transient error"))
                .when(entryService).updateStatus(100L, Status.REJECTED, "admin");
        doNothing().when(entryService).updateStatus(101L, Status.REJECTED, "admin");

        BulkStatusResponse resp = bulkService.bulkUpdate(List.of(100L, 101L), Status.REJECTED, "admin");

        // Despite 100L failing, 101L must have been attempted and succeeded
        assertThat(resp.getSuccessCount()).isEqualTo(1);
        assertThat(resp.getFailures()).hasSize(1);
        verify(entryService).updateStatus(101L, Status.REJECTED, "admin");
    }
}
