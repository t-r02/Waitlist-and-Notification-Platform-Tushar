package com.waitlist.admin.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesValidationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class TestConfig {}

    // ── Valid secret passes validation ────────────────────────────────────────

    @Test
    void validSecret_contextLoadsSuccessfully() {
        runner.withPropertyValues(
                        "app.jwt.secret=this-is-a-valid-secret-of-at-least-32-bytes!",
                        "app.jwt.expiry-minutes=60")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    // ── Short secret fails @PostConstruct validation ──────────────────────────

    @Test
    void shortSecret_contextFailsToStart() {
        runner.withPropertyValues("app.jwt.secret=tooshort")
                .run(ctx -> {
                    assertThat(ctx).hasFailed();
                    assertThat(ctx.getStartupFailure())
                            .rootCause()
                            .hasMessageContaining("32 bytes");
                });
    }

    @Test
    void nullSecret_contextFailsToStart() {
        // No secret property at all — secret remains null
        runner.run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void exactlyShortSecret_31Bytes_fails() {
        // 31 bytes is one short
        String thirtyOne = "a".repeat(31);
        runner.withPropertyValues("app.jwt.secret=" + thirtyOne)
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void exactlyMinimumLength_32Bytes_succeeds() {
        String thirtyTwo = "a".repeat(32);
        runner.withPropertyValues(
                        "app.jwt.secret=" + thirtyTwo,
                        "app.jwt.expiry-minutes=60")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }
}
