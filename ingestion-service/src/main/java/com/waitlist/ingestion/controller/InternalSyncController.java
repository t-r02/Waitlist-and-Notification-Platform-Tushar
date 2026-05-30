package com.waitlist.ingestion.controller;

import com.waitlist.ingestion.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoints for admin-service → ingestion-service Redis sync.
 * NOT intended for public use; only reachable within the Docker network
 * (nginx exposes /api/public/ to the outside, so these paths are also
 * technically reachable but are read-safe and idempotent).
 */
@Slf4j
@RestController
@RequestMapping("/api/public/internal")
@RequiredArgsConstructor
public class InternalSyncController {

    private final LeaderboardService leaderboardService;

    /** Called by admin-service after blacklisting a referrer. */
    @PostMapping("/leaderboard/remove")
    public ResponseEntity<Void> removeFromLeaderboard(@RequestParam String email) {
        leaderboardService.removeFromLeaderboard(email);
        return ResponseEntity.ok().build();
    }

    /** Called by admin-service after whitelisting a referrer. */
    @PostMapping("/leaderboard/restore")
    public ResponseEntity<Void> restoreToLeaderboard(@RequestParam String email) {
        leaderboardService.restoreToLeaderboard(email);
        return ResponseEntity.ok().build();
    }

    /** Rebuilds all-time leaderboard:all from DB — useful after bulk admin changes. */
    @PostMapping("/leaderboard/reconcile")
    public ResponseEntity<Void> reconcile() {
        leaderboardService.reconcile();
        return ResponseEntity.ok().build();
    }
}
