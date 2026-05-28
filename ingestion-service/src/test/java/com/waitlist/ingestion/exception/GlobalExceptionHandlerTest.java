package com.waitlist.ingestion.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc test for the ingestion-service {@link GlobalExceptionHandler}.
 * Uses a minimal inner controller to trigger each exception path.
 */
class GlobalExceptionHandlerTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/test/illegal-arg")
        void illegalArg() { throw new IllegalArgumentException("bad input"); }

        @GetMapping("/test/illegal-state")
        void illegalState() { throw new IllegalStateException("conflict detected"); }

        @GetMapping("/test/not-found")
        void notFound() { throw new NoSuchElementException("item not found"); }

        @GetMapping("/test/data-integrity")
        void dataIntegrity() {
            throw new DataIntegrityViolationException("constraint violation",
                    new RuntimeException("duplicate key"));
        }

        @GetMapping("/test/unexpected")
        void unexpected() { throw new RuntimeException("boom"); }

        @PostMapping("/test/validation")
        ResponseEntity<Void> validation(@Valid @RequestBody ValidatedDto dto) {
            return ResponseEntity.ok().build();
        }

        @PostMapping("/test/malformed")
        ResponseEntity<Void> malformed(@RequestBody ValidatedDto dto) {
            return ResponseEntity.ok().build();
        }
    }

    static class ValidatedDto {
        @NotBlank(message = "name is required")
        private String name;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @Test
    void illegalArgument_returns400WithMessage() throws Exception {
        mockMvc.perform(get("/test/illegal-arg"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("bad input"));
    }

    @Test
    void illegalState_returns409WithMessage() throws Exception {
        mockMvc.perform(get("/test/illegal-state"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("conflict detected"));
    }

    @Test
    void noSuchElement_returns404WithMessage() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("item not found"));
    }

    @Test
    void dataIntegrityViolation_returns409WithGenericMessage() throws Exception {
        mockMvc.perform(get("/test/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Request conflicts with existing data"));
    }

    @Test
    void unexpectedException_returns500WithCorrelationId() throws Exception {
        mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void malformedJson_returns400WithMessage() throws Exception {
        mockMvc.perform(post("/test/malformed")
                        .contentType(APPLICATION_JSON)
                        .content("NOT JSON AT ALL"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed or unreadable request body"));
    }

    @Test
    void validationFailure_returns400WithErrorsArray() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0]").value("name: name is required"));
    }
}
