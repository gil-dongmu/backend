package com.gildongmu.gildongmu_backend.auth.service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.gildongmu.gildongmu_backend.auth.dto.LoginResponse;
import com.gildongmu.gildongmu_backend.auth.dto.TokenResponse;
import com.gildongmu.gildongmu_backend.auth.jwt.JwtProvider;
import com.gildongmu.gildongmu_backend.auth.oauth.OAuthClient;
import com.gildongmu.gildongmu_backend.auth.oauth.OAuthUserInfo;
import com.gildongmu.gildongmu_backend.global.exception.CustomException;
import com.gildongmu.gildongmu_backend.global.exception.ErrorCode;
import com.gildongmu.gildongmu_backend.user.entity.Provider;
import com.gildongmu.gildongmu_backend.user.entity.User;
import com.gildongmu.gildongmu_backend.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class AuthService {

    private final Map<Provider, OAuthClient> oauthClients;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            List<OAuthClient> oauthClients,
            UserRepository userRepository,
            JwtProvider jwtProvider,
            RefreshTokenService refreshTokenService) {
        this.oauthClients = oauthClients.stream()
                .collect(Collectors.toMap(OAuthClient::supports, Function.identity()));
        this.userRepository = userRepository;
        this.jwtProvider = jwtProvider;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public LoginResponse login(Provider provider, String socialAccessToken) {
        OAuthClient client = oauthClients.get(provider);
        if (client == null) {
            throw new CustomException(ErrorCode.UNSUPPORTED_PROVIDER);
        }

        OAuthUserInfo userInfo = client.getUserInfo(socialAccessToken);

        boolean[] isNewUser = {false};
        User user = userRepository
                .findByProviderAndProviderId(userInfo.provider(), userInfo.providerId())
                .orElseGet(() -> {
                    isNewUser[0] = true;
                    log.info("신규 회원 가입: provider={}, providerId={}",
                            userInfo.provider().getCode(), userInfo.providerId());
                    return userRepository.save(User.register(
                            userInfo.provider(),
                            userInfo.providerId(),
                            userInfo.email()));
                });

        String accessToken = jwtProvider.createAccessToken(user.getId());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        refreshTokenService.save(user.getId(), refreshToken);

        return new LoginResponse(accessToken, refreshToken, isNewUser[0]);
    }

    public TokenResponse reissue(String refreshToken) {
        Long userId = jwtProvider.parseUserId(refreshToken);
        if (!refreshTokenService.isValid(userId, refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String newAccessToken = jwtProvider.createAccessToken(userId);
        String newRefreshToken = jwtProvider.createRefreshToken(userId);
        refreshTokenService.save(userId, newRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    public void logout(Long userId) {
        refreshTokenService.delete(userId);
        log.info("로그아웃: userId={}", userId);
    }
}
