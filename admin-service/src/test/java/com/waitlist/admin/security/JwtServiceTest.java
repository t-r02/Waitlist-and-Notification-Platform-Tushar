package com.waitlist.admin.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-for-unit-tests-at-least-32-bytes!!";

    JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setExpiryMinutes(60);
        jwtService = new JwtService(props);
    }

    @Test
    void generateToken_returnsNonNullString() {
        String token = jwtService.generateToken("admin");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    void parseToken_returnsCorrectSubject() {
        String token = jwtService.generateToken("admin");
        Claims claims = jwtService.parseToken(token);
        assertThat(claims.getSubject()).isEqualTo("admin");
    }

    @Test
    void parseToken_expiredToken_throwsExpiredJwtException() {
        // 0-minute expiry token expires immediately
        JwtProperties shortProps = new JwtProperties();
        shortProps.setSecret(SECRET);
        shortProps.setExpiryMinutes(0);
        JwtService shortService = new JwtService(shortProps);

        String token = shortService.generateToken("admin");

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void parseToken_wrongSecret_throwsException() {
        // Token signed with a different secret
        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("another-secret-for-unit-tests-at-least-32bytes");
        JwtService otherService = new JwtService(otherProps);

        String token = otherService.generateToken("admin");

        assertThatThrownBy(() -> jwtService.parseToken(token))
                .isInstanceOf(io.jsonwebtoken.security.SignatureException.class);
    }

    @Test
    void generateToken_differentUsernames_produceDifferentTokens() {
        String t1 = jwtService.generateToken("alice");
        String t2 = jwtService.generateToken("bob");
        assertThat(t1).isNotEqualTo(t2);
    }
}
