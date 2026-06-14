package com.gildongmu.gildongmu_backend.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, boolean isNewUser) {
}
