package com.waitlist.ingestion.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Configuration
public class RateLimitConfig {

    /** Coarse global safety net: 1 000 requests per minute across all clients. */
    @Bean
    public Bucket globalRateLimitBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.classic(1_000, Refill.intervally(1_000, Duration.ofMinutes(1))))
                .build();
    }

    /**
     * Per-IP buckets: 10 requests per minute per client IP.
     * Entries expire 1 hour after last access so the map stays bounded.
     */
    @Bean
    public LoadingCache<String, Bucket> perIpBuckets() {
        return Caffeine.newBuilder()
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build(ip -> Bucket.builder()
                        .addLimit(Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1))))
                        .build());
    }
}
