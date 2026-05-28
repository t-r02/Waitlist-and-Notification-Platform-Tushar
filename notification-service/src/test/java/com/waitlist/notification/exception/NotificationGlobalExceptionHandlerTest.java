package com.waitlist.notification.exception;

import com.waitlist.notification.filter.CorrelationIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc test for the notification-service {@link GlobalExceptionHandler}.
 */
class NotificationGlobalExceptionHandlerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(new CorrelationIdFilter())
                .build();
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/illegal-state")
        void illegalState() { throw new IllegalStateException("conflict"); }

        @GetMapping("/test/not-found")
        void notFound() { throw new NoSuchElementException("not found"); }

        @GetMapping("/test/unexpected")
        void unexpected() { throw new RuntimeException("boom"); }

        @GetMapping("/test/ok")
        ResponseEntity<String> ok() { return ResponseEntity.ok("ok"); }
    }

    @Test
    void illegalState_returns409() throws Exception {
        mockMvc.perform(get("/test/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("conflict"));
    }

    @Test
    void noSuchElement_returns404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void unexpectedException_returns500WithCorrelationId() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void successResponse_isPassedThrough() throws Exception {
        mockMvc.perform(get("/test/ok"))
                .andExpect(status().isOk());
    }
}
