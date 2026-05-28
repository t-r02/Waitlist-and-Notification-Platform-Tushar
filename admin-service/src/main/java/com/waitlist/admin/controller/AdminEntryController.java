package com.waitlist.admin.controller;

import com.waitlist.admin.dto.request.BulkStatusRequest;
import com.waitlist.admin.dto.response.WaitlistEntryResponse;
import com.waitlist.admin.entity.Status;
import com.waitlist.admin.mapper.WaitlistEntryMapper;
import com.waitlist.admin.service.BulkStatusService;
import com.waitlist.admin.service.EntryManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/entries")
@RequiredArgsConstructor
public class AdminEntryController {

    private final EntryManagementService entryService;
    private final BulkStatusService      bulkService;
    private final WaitlistEntryMapper    mapper;

    @GetMapping
    public ResponseEntity<List<WaitlistEntryResponse>> list(
            @RequestParam(required = false) Status status) {
        var entries = status != null
                ? entryService.filterByStatus(status)
                : entryService.listAll();
        return ResponseEntity.ok(mapper.toResponseList(entries));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> updateStatus(@PathVariable Long id,
                                             @RequestParam Status status,
                                             Authentication auth) {
        entryService.updateStatus(id, status, auth.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/bulk")
    public ResponseEntity<?> bulkUpdate(@Valid @RequestBody BulkStatusRequest req,
                                        Authentication auth) {
        var result = bulkService.bulkUpdate(req.getIds(), req.getNewStatus(), auth.getName());
        return result.getFailures().isEmpty()
                ? ResponseEntity.ok(result)
                : ResponseEntity.status(207).body(result);
    }
}
