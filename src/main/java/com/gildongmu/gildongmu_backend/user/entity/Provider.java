package com.gildongmu.gildongmu_backend.user.entity;

import java.util.Arrays;
import com.gildongmu.gildongmu_backend.global.exception.CustomException;
import com.gildongmu.gildongmu_backend.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Provider {

    KAKAO("kakao"),
    NAVER("naver");

    // DB 및 URL path에 노출되는 소문자 코드
    private final String code;

    public static Provider from(String code) {
        return Arrays.stream(values())
                .filter(provider -> provider.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.UNSUPPORTED_PROVIDER));
    }
}
