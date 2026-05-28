package com.waitlist.ingestion.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SignupRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    private String email;

    @Size(max = 120, message = "name must be 120 characters or fewer")
    private String name;

    @Size(max = 120, message = "company must be 120 characters or fewer")
    private String company;

    // Nullable — @Pattern skips null values by default
    @Pattern(
        regexp = "[a-z0-9]{8}",
        flags = Pattern.Flag.CASE_INSENSITIVE,
        message = "referralCode must be exactly 8 alphanumeric characters"
    )
    private String referralCode;

    // Honeypot field: legitimate clients never populate this.
    // Checked manually in SignupController before the service is called —
    // no validation annotation needed (and @AssertNull has spotty Hibernate Validator support).
    private String website;
}
