package com.waitlist.ingestion.controller;

import com.waitlist.ingestion.dto.request.SignupRequest;
import com.waitlist.ingestion.dto.response.ProfileResponse;
import com.waitlist.ingestion.dto.response.SignupResponse;
import com.waitlist.ingestion.filter.RateLimitInterceptor;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import com.waitlist.ingestion.service.LeaderboardService;
import com.waitlist.ingestion.service.SignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class SignupController {

    private final SignupService            signupService;
    private final LeaderboardService       leaderboardService;
    private final WaitlistEntryRepository  entryRepository;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest req,
                                                 HttpServletRequest httpRequest) {
        // Honeypot: legitimate clients never populate the hidden `website` field.
        // Return a convincing fake-success so bots cannot detect the filter.
        if (req.getWebsite() != null) {
            String ip = RateLimitInterceptor.extractClientIp(httpRequest);
            log.warn("Honeypot triggered — possible bot [ip={}]", ip);
            return ResponseEntity.ok(new SignupResponse("Already registered", "00000000", true, true));
        }

        return ResponseEntity.ok(signupService.signup(req));
    }

    /**
     * Profile lookup — returns the referral code for an existing registrant.
     * Does NOT create a new entry; returns 404 if the email is not on the list.
     * Returns {@code verified: false} with a null referralCode for unverified users.
     */
    @GetMapping("/profile")
    public ResponseEntity<ProfileResponse> profile(@RequestParam String email) {
        var normalized = email.trim().toLowerCase();
        var entryOpt = entryRepository.findByEmail(normalized);
        if (entryOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var entry = entryOpt.get();
        return ResponseEntity.ok(new ProfileResponse(
                entry.getEmail(),
                entry.isVerified() ? entry.getReferralCode() : null,
                entry.isVerified()));
    }

    /**
     * Returns the top-10 leaderboard.
     *
     * @param window {@code all} (default) for all-time, {@code week} for the
     *               current ISO week.  An unrecognised value yields 400.
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<?> leaderboard(
            @RequestParam(name = "window", defaultValue = "all") String window) {
        return ResponseEntity.ok(leaderboardService.getLeaderboard(window));
    }
}
