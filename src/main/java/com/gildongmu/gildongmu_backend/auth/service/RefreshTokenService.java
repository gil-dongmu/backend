package com.gildongmu.gildongmu_backend.auth.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

	private static final String KEY_PREFIX = "refresh:";

	private final StringRedisTemplate redisTemplate;
	private final long refreshTokenValidityMs;

	public RefreshTokenService(
			StringRedisTemplate redisTemplate,
			@Value("${jwt.refresh-token-validity-ms}") long refreshTokenValidityMs) {
		this.redisTemplate = redisTemplate;
		this.refreshTokenValidityMs = refreshTokenValidityMs;
	}

	public void save(Long userId, String refreshToken) {
		redisTemplate.opsForValue().set(
				key(userId),
				refreshToken,
				Duration.ofMillis(refreshTokenValidityMs).toMillis(),
				TimeUnit.MILLISECONDS);
	}

	public boolean isValid(Long userId, String refreshToken) {
		String stored = redisTemplate.opsForValue().get(key(userId));
		return stored != null && stored.equals(refreshToken);
	}

	public void delete(Long userId) {
		redisTemplate.delete(key(userId));
	}

	private String key(Long userId) {
		return KEY_PREFIX + userId;
	}
}
