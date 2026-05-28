package com.waitlist.admin.security;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    private String secret;
    private int expiryMinutes = 1440;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                "app.jwt.secret must be at least 32 bytes long. " +
                "Current length: " + (secret == null ? 0 :
                    secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length));
        }
    }

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public int getExpiryMinutes() { return expiryMinutes; }
    public void setExpiryMinutes(int expiryMinutes) { this.expiryMinutes = expiryMinutes; }
}
