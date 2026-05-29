package com.waitlist.ingestion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.waitlist.ingestion.dto.response.LeaderboardEntry;
import com.waitlist.ingestion.dto.response.SignupResponse;
import com.waitlist.ingestion.entity.WaitlistEntry;
import com.waitlist.ingestion.repository.WaitlistEntryRepository;
import com.waitlist.ingestion.service.LeaderboardService;
import com.waitlist.ingestion.service.SignupService;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SignupController.class)
@ActiveProfiles("test")
class SignupControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean SignupService           signupService;
    @MockBean LeaderboardService      leaderboardService;
    @MockBean WaitlistEntryRepository entryRepository;
    @MockBean Bucket                  globalRateLimitBucket;
    @SuppressWarnings("rawtypes")
    @MockBean LoadingCache       perIpBuckets;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void allowAllRequests() {
        ConsumptionProbe globalProbe = mock(ConsumptionProbe.class);
        when(globalProbe.isConsumed()).thenReturn(true);
        when(globalRateLimitBucket.tryConsumeAndReturnRemaining(1)).thenReturn(globalProbe);

        Bucket ipBucket = mock(Bucket.class);
        ConsumptionProbe ipProbe = mock(ConsumptionProbe.class);
        when(ipProbe.isConsumed()).thenReturn(true);
        when(ipBucket.tryConsumeAndReturnRemaining(1)).thenReturn(ipProbe);
        when(perIpBuckets.get(any())).thenReturn(ipBucket);
    }

    // ── POST /api/public/signup ────────────────────────────────────────────────

    @Test
    void signup_validRequest_returns200WithResponse() throws Exception {
        var body = Map.of("email", "alice@example.com", "name", "Alice");
        when(signupService.signup(any()))
                .thenReturn(new SignupResponse("Please verify your email to complete registration",
                        null, false, false));

        mockMvc.perform(post("/api/public/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false))
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void signup_honeypotFieldPopulated_returnsFakeSuccessWithoutCallingService() throws Exception {
        var body = Map.of("email", "bot@example.com", "name", "Bot", "website", "http://evil.com");

        mockMvc.perform(post("/api/public/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referralCode").value("00000000"));

        verify(signupService, never()).signup(any());
    }

    @Test
    void signup_missingEmail_returns400WithValidationError() throws Exception {
        var body = Map.of("name", "NoEmail");

        mockMvc.perform(post("/api/public/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void signup_malformedEmail_returns400() throws Exception {
        var body = Map.of("email", "not-an-email", "name", "Bad");

        mockMvc.perform(post("/api/public/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signup_duplicateEmail_returns200WithDuplicateFlag() throws Exception {
        var body = Map.of("email", "dup@example.com", "name", "Dup");
        when(signupService.signup(any()))
                .thenReturn(new SignupResponse("Already registered", "existref", true, true));

        mockMvc.perform(post("/api/public/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void signup_nameMismatchOnVerifiedEmail_returns400() throws Exception {
        var body = Map.of("email", "alice@example.com", "name", "Wrong Name");
        when(signupService.signup(any()))
                .thenThrow(new IllegalArgumentException(
                        "This email is already registered. Please use the name you originally signed up with."));

        mockMvc.perform(post("/api/public/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").isString());
    }

    // ── GET /api/public/leaderboard ───────────────────────────────────────────

    @Test
    void leaderboard_allWindow_returns200() throws Exception {
        when(leaderboardService.getLeaderboard("all"))
                .thenReturn(List.of(new LeaderboardEntry("alice@example.com", 30, "SILVER")));

        mockMvc.perform(get("/api/public/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$[0].points").value(30));
    }

    @Test
    void leaderboard_weekWindow_returns200() throws Exception {
        when(leaderboardService.getLeaderboard("week"))
                .thenReturn(List.of(new LeaderboardEntry("bob@example.com", 10, null)));

        mockMvc.perform(get("/api/public/leaderboard").param("window", "week"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("bob@example.com"));
    }

    @Test
    void leaderboard_unknownWindow_returns400ViaExceptionHandler() throws Exception {
        when(leaderboardService.getLeaderboard("monthly"))
                .thenThrow(new IllegalArgumentException("Unknown leaderboard window 'monthly'"));

        mockMvc.perform(get("/api/public/leaderboard").param("window", "monthly"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── GET /api/public/profile ───────────────────────────────────────────────

    @Test
    void profile_verifiedEmail_returnsReferralCode() throws Exception {
        var entry = new WaitlistEntry();
        entry.setEmail("alice@example.com");
        entry.setReferralCode("abc12345");
        entry.setVerified(true);
        when(entryRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(entry));

        mockMvc.perform(get("/api/public/profile").param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referralCode").value("abc12345"))
                .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void profile_unverifiedEmail_returnsNullReferralCode() throws Exception {
        var entry = new WaitlistEntry();
        entry.setEmail("bob@example.com");
        entry.setReferralCode("shouldbehidden");
        entry.setVerified(false);
        when(entryRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(entry));

        mockMvc.perform(get("/api/public/profile").param("email", "bob@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referralCode").doesNotExist())
                .andExpect(jsonPath("$.verified").value(false));
    }

    @Test
    void profile_unknownEmail_returns404() throws Exception {
        when(entryRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/public/profile").param("email", "nobody@example.com"))
                .andExpect(status().isNotFound());
    }
}
