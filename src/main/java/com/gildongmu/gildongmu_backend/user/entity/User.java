package com.gildongmu.gildongmu_backend.user.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Convert(converter = ProviderConverter.class)
    @Column(nullable = false, length = 10)
    private Provider provider;

    @Column(name = "provider_id", nullable = false, length = 100)
    private String providerId;

    // 카카오 미동의 시 null 허용
    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String nickname;

    @Column(name = "pref_radius_km", nullable = false)
    private int prefRadiusKm;

    @Column(name = "alarm_enabled", nullable = false)
    private boolean alarmEnabled;

    @Column(name = "alarm_cooldown_min", nullable = false)
    private int alarmCooldownMin;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private User(Provider provider, String providerId, String email) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.prefRadiusKm = 5;
        this.alarmEnabled = true;
        this.alarmCooldownMin = 30;
        this.createdAt = LocalDateTime.now();
    }

    public static User register(Provider provider, String providerId, String email) {
        return new User(provider, providerId, email);
    }

    // 닉네임을 설정한다.
    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }
}
