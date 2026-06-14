package com.gildongmu.gildongmu_backend.auth.controller;

import com.gildongmu.gildongmu_backend.auth.dto.LoginResponse;
import com.gildongmu.gildongmu_backend.auth.dto.ReissueRequest;
import com.gildongmu.gildongmu_backend.auth.dto.SocialLoginRequest;
import com.gildongmu.gildongmu_backend.auth.dto.TokenResponse;
import com.gildongmu.gildongmu_backend.auth.service.AuthService;
import com.gildongmu.gildongmu_backend.user.entity.Provider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login/{provider}")
    public ResponseEntity<LoginResponse> login(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request) {
        LoginResponse response = authService.login(Provider.from(provider), request.accessToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reissue")
    public ResponseEntity<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ResponseEntity.ok(authService.reissue(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal Long userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }
}
