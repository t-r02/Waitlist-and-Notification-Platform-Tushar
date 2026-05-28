package com.waitlist.ingestion.filter;

import com.github.benmanes.caffeine.cache.LoadingCache;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final Bucket globalRateLimitBucket;
    private final LoadingCache<String, Bucket> perIpBuckets;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {
        String ip = extractClientIp(request);

        // Check global safety net first — cheapest rejection path
        ConsumptionProbe globalProbe = globalRateLimitBucket.tryConsumeAndReturnRemaining(1);
        if (!globalProbe.isConsumed()) {
            rejectWith429(response, globalProbe.getNanosToWaitForRefill());
            log.warn("Global rate limit exceeded [ip={}]", ip);
            return false;
        }

        // Per-IP check
        Bucket ipBucket = perIpBuckets.get(ip);
        ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
        if (!ipProbe.isConsumed()) {
            // Return the global token — the request wasn't served
            globalRateLimitBucket.addTokens(1);
            rejectWith429(response, ipProbe.getNanosToWaitForRefill());
            log.warn("Per-IP rate limit exceeded [ip={}]", ip);
            return false;
        }

        return true;
    }

    private static void rejectWith429(HttpServletResponse response, long nanosToWait) throws Exception {
        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(nanosToWait) + 1;
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"status\":429,\"message\":\"Too many requests — please retry after "
                + retryAfterSeconds + " seconds\"}");
    }

    public static String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
