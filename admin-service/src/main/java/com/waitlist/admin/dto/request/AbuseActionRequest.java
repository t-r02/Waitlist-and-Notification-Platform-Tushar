package com.waitlist.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AbuseActionRequest {

    @NotBlank(message = "reason is required")
    private String reason;
}
