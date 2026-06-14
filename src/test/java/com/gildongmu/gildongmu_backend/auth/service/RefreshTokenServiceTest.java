package com.gildongmu.gildongmu_backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class RefreshTokenServiceTest {

	@Autowired
	private RefreshTokenService refreshTokenService;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Test
	void 저장한_리프레시토큰을_검증하고_삭제한다() {
		Long userId = 777L;
		refreshTokenService.save(userId, "refresh-abc");

		assertThat(refreshTokenService.isValid(userId, "refresh-abc")).isTrue();
		assertThat(refreshTokenService.isValid(userId, "wrong")).isFalse();

		refreshTokenService.delete(userId);
		assertThat(refreshTokenService.isValid(userId, "refresh-abc")).isFalse();

		redisTemplate.delete("refresh:" + userId);
	}
}
