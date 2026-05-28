package com.waitlist.ingestion.filter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorrelationIdFilterTest {

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new EchoController())
                .addFilter(new CorrelationIdFilter())
                .build();
    }

    @RestController
    static class EchoController {
        @GetMapping("/test/echo")
        ResponseEntity<String> echo() {
            return ResponseEntity.ok("ok");
        }
    }

    @Test
    void existingCorrelationId_isEchoedInResponseHeader() throws Exception {
        String correlationId = "my-trace-id-123";

        mockMvc.perform(get("/test/echo")
                        .header(CorrelationIdFilter.HEADER, correlationId))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER, correlationId));
    }

    @Test
    void missingCorrelationId_generatesUuidAndSetsResponseHeader() throws Exception {
        String responseHeader = mockMvc.perform(get("/test/echo"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(CorrelationIdFilter.HEADER);

        assertThat(responseHeader).isNotNull();
        // Must be a valid UUID
        assertThat(responseHeader)
                .matches(Pattern.compile(
                        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
                        Pattern.CASE_INSENSITIVE));
    }

    @Test
    void blankCorrelationId_isReplacedWithGeneratedUuid() throws Exception {
        String responseHeader = mockMvc.perform(get("/test/echo")
                        .header(CorrelationIdFilter.HEADER, "   "))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getHeader(CorrelationIdFilter.HEADER);

        // Blank input → should generate a UUID, not echo the blanks
        assertThat(responseHeader).isNotBlank();
        // Validate it is a UUID
        assertThat(UUID.fromString(responseHeader)).isNotNull();
    }

    @Test
    void twoRequestsWithoutHeader_getDistinctCorrelationIds() throws Exception {
        String id1 = mockMvc.perform(get("/test/echo"))
                .andReturn().getResponse().getHeader(CorrelationIdFilter.HEADER);
        String id2 = mockMvc.perform(get("/test/echo"))
                .andReturn().getResponse().getHeader(CorrelationIdFilter.HEADER);

        assertThat(id1).isNotEqualTo(id2);
    }
}
