package com.gildongmu.gildongmu_backend.auth.oauth;

import com.gildongmu.gildongmu_backend.user.entity.Provider;

public record OAuthUserInfo(
        Provider provider,
        String providerId,
        String email,
        String nickname) {
}
