package com.gildongmu.gildongmu_backend.auth.oauth;

import java.util.Map;
import com.gildongmu.gildongmu_backend.global.exception.CustomException;
import com.gildongmu.gildongmu_backend.global.exception.ErrorCode;
import com.gildongmu.gildongmu_backend.user.entity.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class KakaoOAuthClient implements OAuthClient {

    private final RestClient restClient;
    private final String userInfoUri;

    public KakaoOAuthClient(@Value("${oauth.kakao.user-info-uri}") String userInfoUri) {
        this.restClient = RestClient.create();
        this.userInfoUri = userInfoUri;
    }

    @Override
    public Provider supports() {
        return Provider.KAKAO;
    }

    @Override
    @SuppressWarnings("unchecked")
    public OAuthUserInfo getUserInfo(String accessToken) {
        try {
            Map<String, Object> body = restClient.get()
                    .uri(userInfoUri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);

            // 카카오 id는 Long으로 응답되므로 String 변환
            String providerId = String.valueOf(body.get("id"));

            Map<String, Object> account = (Map<String, Object>) body.get("kakao_account");
            String email = account == null ? null : (String) account.get("email");

            String nickname = "사용자";
            if (account != null && account.get("profile") instanceof Map<?, ?> profile) {
                Object name = ((Map<String, Object>) profile).get("nickname");
                if (name != null) {
                    nickname = (String) name;
                }
            }

            return new OAuthUserInfo(Provider.KAKAO, providerId, email, nickname);
        } catch (Exception e) {
            log.error("카카오 사용자 정보 조회 실패", e);
            throw new CustomException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }
}
