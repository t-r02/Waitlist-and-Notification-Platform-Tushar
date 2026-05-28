package com.waitlist.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waitlist.admin.entity.Status;
import com.waitlist.admin.entity.WaitlistEntry;
import com.waitlist.admin.messaging.producer.StatusChangedProducer;
import com.waitlist.admin.repository.WaitlistEntryRepository;
import com.waitlist.admin.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminEntryControllerTest {

    @Autowired MockMvc                  mockMvc;
    @Autowired ObjectMapper             objectMapper;
    @Autowired JwtService               jwtService;
    @Autowired WaitlistEntryRepository  entryRepo;

    // Kafka producer not needed in tests — prevents broker connection attempts
    @MockBean StatusChangedProducer statusChangedProducer;

    String token;

    @BeforeEach
    void setUp() {
        token = "Bearer " + jwtService.generateToken("admin");
    }

    private WaitlistEntry savedEntry(String email, Status status) {
        var e = new WaitlistEntry();
        e.setIngestionId(System.currentTimeMillis());
        e.setEmail(email);
        e.setName("Test User");
        e.setStatus(status);
        return entryRepo.save(e);
    }

    // ── GET /api/admin/entries ────────────────────────────────────────────────

    @Test
    void listEntries_withValidToken_returns200() throws Exception {
        mockMvc.perform(get("/api/admin/entries").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void listEntries_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/entries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listEntries_filterByStatus_returns200() throws Exception {
        savedEntry("filter-test@example.com", Status.APPROVED);

        mockMvc.perform(get("/api/admin/entries")
                        .param("status", "APPROVED")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='filter-test@example.com')]").exists());
    }

    @Test
    void listEntries_invalidStatusValue_returns400() throws Exception {
        mockMvc.perform(get("/api/admin/entries")
                        .param("status", "BOGUS")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── PATCH /api/admin/entries/{id} ─────────────────────────────────────────

    @Test
    void updateStatus_validTransition_returns200() throws Exception {
        var entry = savedEntry("patch-me@example.com", Status.PENDING);

        mockMvc.perform(patch("/api/admin/entries/" + entry.getId())
                        .param("status", "APPROVED")
                        .header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    void updateStatus_entryNotFound_returns404() throws Exception {
        mockMvc.perform(patch("/api/admin/entries/999999")
                        .param("status", "APPROVED")
                        .header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void updateStatus_invalidTransition_returns409() throws Exception {
        var entry = savedEntry("invited@example.com", Status.INVITED);

        mockMvc.perform(patch("/api/admin/entries/" + entry.getId())
                        .param("status", "APPROVED")
                        .header("Authorization", token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void updateStatus_invalidStatusValue_returns400() throws Exception {
        var entry = savedEntry("type-mismatch@example.com", Status.PENDING);

        mockMvc.perform(patch("/api/admin/entries/" + entry.getId())
                        .param("status", "BOGUS")
                        .header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void updateStatus_withoutToken_returns401() throws Exception {
        var entry = savedEntry("no-auth@example.com", Status.PENDING);

        mockMvc.perform(patch("/api/admin/entries/" + entry.getId())
                        .param("status", "APPROVED"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/admin/entries/bulk ──────────────────────────────────────────

    @Test
    void bulkUpdate_allSucceed_returns200() throws Exception {
        var e1 = savedEntry("bulk1@example.com", Status.PENDING);
        var e2 = savedEntry("bulk2@example.com", Status.PENDING);

        var body = Map.of("ids", List.of(e1.getId(), e2.getId()), "newStatus", "APPROVED");

        mockMvc.perform(post("/api/admin/entries/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.failures").isEmpty());
    }

    @Test
    void bulkUpdate_someFail_returns207WithPartialResults() throws Exception {
        var good = savedEntry("bulk-good@example.com", Status.PENDING);

        var body = Map.of("ids", List.of(good.getId(), 888888L), "newStatus", "APPROVED");

        mockMvc.perform(post("/api/admin/entries/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", token))
                .andExpect(status().is(207))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.failures").isNotEmpty());
    }

    @Test
    void bulkUpdate_emptyIds_returns400ValidationError() throws Exception {
        var body = Map.of("ids", List.of(), "newStatus", "APPROVED");

        mockMvc.perform(post("/api/admin/entries/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body))
                        .header("Authorization", token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bulkUpdate_withoutToken_returns401() throws Exception {
        var body = Map.of("ids", List.of(1L), "newStatus", "APPROVED");

        mockMvc.perform(post("/api/admin/entries/bulk")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
    }
}
