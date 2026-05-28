package com.waitlist.ingestion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SignupResponse {
    private String message;
    private String referralCode;
    private boolean duplicate;
}
