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
public class NaverOAuthClient implements OAuthClient {

    private final RestClient restClient;
    private final String userInfoUri;

    public NaverOAuthClient(@Value("${oauth.naver.user-info-uri}") String userInfoUri) {
        this.restClient = RestClient.create();
        this.userInfoUri = userInfoUri;
    }

    @Override
    public Provider supports() {
        return Provider.NAVER;
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

            Map<String, Object> response = (Map<String, Object>) body.get("response");
            if (response == null) {
                throw new CustomException(ErrorCode.INVALID_SOCIAL_TOKEN);
            }

            // 네이버 id는 String으로 응답되므로 그대로 사용
            String providerId = (String) response.get("id");
            String email = (String) response.get("email");

            return new OAuthUserInfo(Provider.NAVER, providerId, email);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("네이버 사용자 정보 조회 실패", e);
            throw new CustomException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }
}
