package com.waitlist.ingestion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SignupResponse {
    private String message;
    /** Null for newly registered unverified users — revealed after email verification. */
    private String referralCode;
    private boolean duplicate;
    private boolean verified;
}
