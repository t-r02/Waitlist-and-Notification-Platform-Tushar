package com.waitlist.ingestion.filter;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitInterceptorTest {

    RateLimitInterceptor interceptor;
    Bucket               globalBucket;
    LoadingCache<String, Bucket> perIpBuckets;

    @BeforeEach
    void setUp() {
        // Generous global bucket — most tests target the per-IP limit
        globalBucket = Bucket.builder()
                .addLimit(Bandwidth.classic(1_000, Refill.intervally(1_000, Duration.ofMinutes(1))))
                .build();

        // Per-IP limit: 2 per "minute" — easy to exhaust in tests
        perIpBuckets = Caffeine.newBuilder()
                .build(ip -> Bucket.builder()
                        .addLimit(Bandwidth.classic(2, Refill.intervally(2, Duration.ofMinutes(1))))
                        .build());

        interceptor = new RateLimitInterceptor(globalBucket, perIpBuckets);
    }

    // ── normal request passes ─────────────────────────────────────────────────

    @Test
    void normalRequest_preHandleReturnsTrue() throws Exception {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("1.2.3.4");
        var res = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(req, res, new Object());

        assertThat(allowed).isTrue();
        assertThat(res.getStatus()).isEqualTo(200); // untouched
    }

    // ── per-IP rate limit exceeded ────────────────────────────────────────────

    @Test
    void perIpLimitExceeded_returnsFalseAndSets429() throws Exception {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("10.0.0.1");
        var res = new MockHttpServletResponse();

        // Exhaust the 2-token per-IP bucket
        interceptor.preHandle(req, new MockHttpServletResponse(), new Object());
        interceptor.preHandle(req, new MockHttpServletResponse(), new Object());

        // 3rd request should be rejected
        boolean allowed = interceptor.preHandle(req, res, new Object());

        assertThat(allowed).isFalse();
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isNotNull();
    }

    // ── global rate limit exceeded ────────────────────────────────────────────

    @Test
    void globalLimitExceeded_returnsFalseAndSets429() throws Exception {
        // Override with a 1-token global bucket
        Bucket tinyGlobal = Bucket.builder()
                .addLimit(Bandwidth.classic(1, Refill.intervally(1, Duration.ofMinutes(1))))
                .build();
        interceptor = new RateLimitInterceptor(tinyGlobal, perIpBuckets);

        var req1 = new MockHttpServletRequest();
        req1.setRemoteAddr("2.2.2.2");
        interceptor.preHandle(req1, new MockHttpServletResponse(), new Object()); // consume the 1 token

        var req2 = new MockHttpServletRequest();
        req2.setRemoteAddr("3.3.3.3"); // different IP, so per-IP not exhausted
        var res2 = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(req2, res2, new Object());

        assertThat(allowed).isFalse();
        assertThat(res2.getStatus()).isEqualTo(429);
    }

    // ── global token is returned when per-IP rejects ──────────────────────────

    @Test
    void whenPerIpRejects_globalTokenIsReturnedSoAnotherIpCanStillPass() throws Exception {
        // 2-token global bucket
        Bucket twoGlobal = Bucket.builder()
                .addLimit(Bandwidth.classic(2, Refill.intervally(2, Duration.ofMinutes(1))))
                .build();

        // 1-token per-IP bucket (different factory for different IPs)
        LoadingCache<String, Bucket> onePerIp = Caffeine.newBuilder()
                .build(ip -> Bucket.builder()
                        .addLimit(Bandwidth.classic(1, Refill.intervally(1, Duration.ofMinutes(1))))
                        .build());

        interceptor = new RateLimitInterceptor(twoGlobal, onePerIp);

        // IP A — exhausts its per-IP quota
        var reqA = new MockHttpServletRequest();
        reqA.setRemoteAddr("4.4.4.4");
        interceptor.preHandle(reqA, new MockHttpServletResponse(), new Object()); // success
        boolean rejectedA = !interceptor.preHandle(reqA, new MockHttpServletResponse(), new Object()); // per-IP reject

        assertThat(rejectedA).isTrue();

        // IP B — a different IP should still succeed (global token was refunded)
        var reqB  = new MockHttpServletRequest();
        reqB.setRemoteAddr("5.5.5.5");
        var resB  = new MockHttpServletResponse();
        boolean allowedB = interceptor.preHandle(reqB, resB, new Object());

        assertThat(allowedB).isTrue();
    }

    // ── extractClientIp ───────────────────────────────────────────────────────

    @Test
    void extractClientIp_xForwardedForPresent_returnsFirstIp() {
        var req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.1");

        assertThat(RateLimitInterceptor.extractClientIp(req)).isEqualTo("203.0.113.5");
    }

    @Test
    void extractClientIp_noForwardedFor_fallsBackToRemoteAddr() {
        var req = new MockHttpServletRequest();
        req.setRemoteAddr("192.168.0.42");

        assertThat(RateLimitInterceptor.extractClientIp(req)).isEqualTo("192.168.0.42");
    }

    @Test
    void extractClientIp_blankXForwardedFor_fallsBackToRemoteAddr() {
        var req = new MockHttpServletRequest();
        req.addHeader("X-Forwarded-For", "   ");
        req.setRemoteAddr("172.16.0.1");

        assertThat(RateLimitInterceptor.extractClientIp(req)).isEqualTo("172.16.0.1");
    }
}
