package com.gildongmu.gildongmu_backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import com.gildongmu.gildongmu_backend.auth.dto.LoginResponse;
import com.gildongmu.gildongmu_backend.auth.jwt.JwtProvider;
import com.gildongmu.gildongmu_backend.auth.oauth.KakaoOAuthClient;
import com.gildongmu.gildongmu_backend.auth.oauth.OAuthUserInfo;
import com.gildongmu.gildongmu_backend.user.entity.Provider;
import com.gildongmu.gildongmu_backend.user.entity.User;
import com.gildongmu.gildongmu_backend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private KakaoOAuthClient kakaoOAuthClient;

    @Test
    void 신규회원이면_가입하고_isNewUser_true를_반환한다() {
        when(kakaoOAuthClient.supports()).thenReturn(Provider.KAKAO);
        AuthService authService = new AuthService(
                List.of(kakaoOAuthClient), userRepository, jwtProvider, refreshTokenService);

        when(kakaoOAuthClient.getUserInfo("social-token"))
                .thenReturn(new OAuthUserInfo(Provider.KAKAO, "kakao-1", "a@b.com", "tester"));
        when(userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-1"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtProvider.createAccessToken(any())).thenReturn("access");
        when(jwtProvider.createRefreshToken(any())).thenReturn("refresh");

        LoginResponse response = authService.login(Provider.KAKAO, "social-token");

        assertThat(response.isNewUser()).isTrue();
        assertThat(response.accessToken()).isEqualTo("access");
        assertThat(response.refreshToken()).isEqualTo("refresh");
    }

    @Test
    void 기존회원이면_isNewUser_false를_반환한다() {
        when(kakaoOAuthClient.supports()).thenReturn(Provider.KAKAO);
        AuthService authService = new AuthService(
                List.of(kakaoOAuthClient), userRepository, jwtProvider, refreshTokenService);

        when(kakaoOAuthClient.getUserInfo("social-token"))
                .thenReturn(new OAuthUserInfo(Provider.KAKAO, "kakao-1", "a@b.com", "tester"));
        when(userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-1"))
                .thenReturn(Optional.of(User.register(Provider.KAKAO, "kakao-1", "a@b.com", "tester")));
        when(jwtProvider.createAccessToken(any())).thenReturn("access");
        when(jwtProvider.createRefreshToken(any())).thenReturn("refresh");

        LoginResponse response = authService.login(Provider.KAKAO, "social-token");

        assertThat(response.isNewUser()).isFalse();
    }
}
