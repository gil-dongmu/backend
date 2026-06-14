package com.gildongmu.gildongmu_backend.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gildongmu.gildongmu_backend.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                "test-secret-key-test-secret-key-test-secret-key-0123456789",
                1800000L,
                2592000000L);
    }

    @Test
    void accessToken을_발급하고_userId를_파싱한다() {
        String token = jwtProvider.createAccessToken(42L);

        Long userId = jwtProvider.parseUserId(token);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void 위조된_토큰은_예외를_던진다() {
        assertThatThrownBy(() -> jwtProvider.parseUserId("tampered.token.value"))
                .isInstanceOf(CustomException.class);
    }
}
