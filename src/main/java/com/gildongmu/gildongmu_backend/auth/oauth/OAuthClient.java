package com.gildongmu.gildongmu_backend.auth.oauth;

import com.gildongmu.gildongmu_backend.user.entity.Provider;

public interface OAuthClient {

    Provider supports();

    OAuthUserInfo getUserInfo(String accessToken);
}
