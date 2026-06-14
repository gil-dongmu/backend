package com.gildongmu.gildongmu_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(@NotBlank String refreshToken) {
}
