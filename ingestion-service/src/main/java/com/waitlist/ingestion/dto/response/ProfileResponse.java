package com.waitlist.ingestion.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ProfileResponse {
    private String email;
    /** Null when the user has not yet verified their email address. */
    private String referralCode;
    private boolean verified;
}
