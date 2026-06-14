# 소셜 로그인 (카카오 / 네이버) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 카카오/네이버 access token을 받아 사용자 정보를 조회하고, 회원 매칭/가입 후 자체 JWT(Access 30분/Refresh 30일)를 발급하는 소셜 로그인 백엔드를 구현한다.

**Architecture:** 모바일 앱이 소셜 SDK로 받은 access token을 백엔드에 전송하면, 전략 패턴 기반 `OAuthClient`가 카카오/네이버 사용자 정보를 조회한다. `provider + provider_id`로 회원을 판별해 로그인/가입하고, JWT를 발급한다. Refresh Token은 Redis(`refresh:{userId}`, TTL 30일)에만 저장한다. 인증은 stateless `JwtAuthenticationFilter`로 처리한다.

**Tech Stack:** Java 21, Spring Boot 4.0.x, Spring Security, Spring Data JPA, Spring Data Redis, jjwt 0.12.x, RestClient, Flyway, PostgreSQL(+PostGIS)/Redis(Docker), JUnit5.

---

## 커밋 정책

이 레포는 단독 개발자가 **모든 커밋을 수동으로** 한다. 각 Task 끝의 "커밋 지점"은 권장 매듭일 뿐이며, 자동으로 `git commit`을 실행하지 않는다. 직접 커밋할 때 CLAUDE.md 규칙(`Type: 설명`)을 따른다.

## 베이스 패키지

루트 패키지는 현재 코드 기준 `com.gildongmu.gildongmu_backend`를 유지한다. 아래 모든 경로는 이 패키지 하위다.

---

## 파일 구조 (생성 대상)

```
build.gradle                                    의존성 추가
docker-compose.yml                              postgis + redis
.gitignore                                      application-local.yml 추가
src/main/resources/application.yml              공통 설정
src/main/resources/application-local.yml        로컬 접속/키 (gitignore)
src/main/resources/db/migration/V1__create_users.sql   users 테이블

src/main/java/com/gildongmu/gildongmu_backend/
├── user/
│   ├── entity/Provider.java
│   ├── entity/ProviderConverter.java
│   ├── entity/User.java
│   └── repository/UserRepository.java
├── global/
│   ├── exception/ErrorCode.java
│   ├── exception/CustomException.java
│   ├── exception/ErrorResponse.java
│   ├── exception/GlobalExceptionHandler.java
│   └── config/SecurityConfig.java
└── auth/
    ├── jwt/JwtProvider.java
    ├── jwt/JwtAuthenticationFilter.java
    ├── jwt/JwtAuthentication.java
    ├── service/RefreshTokenService.java
    ├── oauth/OAuthUserInfo.java
    ├── oauth/OAuthClient.java
    ├── oauth/KakaoOAuthClient.java
    ├── oauth/NaverOAuthClient.java
    ├── service/AuthService.java
    ├── dto/SocialLoginRequest.java
    ├── dto/ReissueRequest.java
    ├── dto/LoginResponse.java
    ├── dto/TokenResponse.java
    └── controller/AuthController.java
```

---

# Phase 0 — 인프라 (Docker → 설정 → 스키마)

### Task 1: build.gradle 의존성 추가

**Files:**
- Modify: `build.gradle`

- [ ] **Step 1: dependencies 블록 교체**

`dependencies { ... }` 블록을 아래로 교체한다.

```gradle
dependencies {
	implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
	implementation 'org.springframework.boot:spring-boot-starter-webmvc'
	implementation 'org.springframework.boot:spring-boot-starter-security'
	implementation 'org.springframework.boot:spring-boot-starter-data-redis'
	implementation 'org.springframework.boot:spring-boot-starter-validation'
	implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.2'
	implementation 'org.flywaydb:flyway-core'
	implementation 'org.flywaydb:flyway-database-postgresql'
	implementation 'io.jsonwebtoken:jjwt-api:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.6'
	runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.6'
	compileOnly 'org.projectlombok:lombok'
	developmentOnly 'org.springframework.boot:spring-boot-devtools'
	runtimeOnly 'org.postgresql:postgresql'
	annotationProcessor 'org.projectlombok:lombok'
	testImplementation 'org.springframework.boot:spring-boot-starter-test'
	testImplementation 'org.springframework.security:spring-security-test'
	testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
	testCompileOnly 'org.projectlombok:lombok'
	testAnnotationProcessor 'org.projectlombok:lombok'
}
```

- [ ] **Step 2: 의존성 해석 확인**

Run: `./gradlew dependencies --configuration compileClasspath -q | grep -E "jjwt|flyway|security|redis" | head`
Expected: jjwt, flyway, security, redis 항목이 출력됨 (FAIL 없이 종료)

- [ ] **Step 3: 커밋 지점 (수동)** — `Chore: 소셜 로그인 의존성 추가`

---

### Task 2: docker-compose.yml 작성

**Files:**
- Create: `docker-compose.yml`

- [ ] **Step 1: 파일 작성**

```yaml
services:
  postgres:
    image: postgis/postgis:16-3.4
    container_name: gildongmu-postgres
    environment:
      POSTGRES_DB: gildongmu
      POSTGRES_USER: gildongmu
      POSTGRES_PASSWORD: gildongmu
    ports:
      - "5432:5432"
    volumes:
      - gildongmu-pg-data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    container_name: gildongmu-redis
    ports:
      - "6379:6379"

volumes:
  gildongmu-pg-data:
```

- [ ] **Step 2: 컨테이너 기동 후 확인**

Run: `docker compose up -d && docker compose ps`
Expected: postgres, redis 둘 다 `running` 상태

- [ ] **Step 3: PostGIS 동작 확인**

Run: `docker exec gildongmu-postgres psql -U gildongmu -d gildongmu -c "CREATE EXTENSION IF NOT EXISTS postgis; SELECT postgis_version();"`
Expected: PostGIS 버전 문자열 출력

- [ ] **Step 4: 커밋 지점 (수동)** — `Chore: 로컬 개발용 docker-compose 추가`

---

### Task 3: 설정 파일 + .gitignore

**Files:**
- Create: `src/main/resources/application.yml` (기존 `application.yaml` 삭제)
- Create: `src/main/resources/application-local.yml`
- Modify: `.gitignore`

- [ ] **Step 1: 기존 application.yaml 삭제**

Run: `rm src/main/resources/application.yaml`

- [ ] **Step 2: application.yml 생성 (공통)**

```yaml
spring:
  application:
    name: gildongmu-backend
  profiles:
    active: local
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true
    open-in-view: false
  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  secret: ${JWT_SECRET}
  access-token-validity-ms: 1800000
  refresh-token-validity-ms: 2592000000

oauth:
  kakao:
    user-info-uri: https://kapi.kakao.com/v2/user/me
  naver:
    user-info-uri: https://openapi.naver.com/v1/nid/me
```

- [ ] **Step 3: application-local.yml 생성 (로컬 전용, gitignore 대상)**

`jwt.secret`은 HS256용으로 최소 32바이트 이상 임의 문자열을 넣는다.

```yaml
spring:
  config:
    activate:
      on-profile: local
  datasource:
    url: jdbc:postgresql://localhost:5432/gildongmu
    username: gildongmu
    password: gildongmu
    driver-class-name: org.postgresql.Driver
  data:
    redis:
      host: localhost
      port: 6379

jwt:
  secret: local-dev-secret-please-change-this-to-32bytes-or-more-0123456789
```

- [ ] **Step 4: .gitignore에 로컬 설정 추가**

`.gitignore` 맨 아래에 추가한다.

```
### Local config ###
src/main/resources/application-local.yml
```

- [ ] **Step 5: 커밋 지점 (수동)** — `Chore: 프로파일 분리 및 공통 설정 추가`

---

### Task 4: Flyway users 마이그레이션

**Files:**
- Create: `src/main/resources/db/migration/V1__create_users.sql`

- [ ] **Step 1: 마이그레이션 작성**

ERD 기준. `provider`는 'kakao'/'naver' 소문자 저장.

```sql
CREATE TABLE users (
    user_id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    provider           VARCHAR(10)  NOT NULL,
    provider_id        VARCHAR(100) NOT NULL,
    email              VARCHAR(255),
    nickname           VARCHAR(50)  NOT NULL,
    pref_radius_km     INT          NOT NULL DEFAULT 5,
    alarm_enabled      BOOLEAN      NOT NULL DEFAULT TRUE,
    alarm_cooldown_min INT          NOT NULL DEFAULT 30,
    created_at         TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_provider UNIQUE (provider, provider_id)
);
```

- [ ] **Step 2: 마이그레이션 적용 확인**

Run: `./gradlew flywayInfo` 가 없으면 앱 부팅으로 대체 — `./gradlew bootRun` 후 로그에 `Migrating schema "public" to version "1 - create users"` 확인 → 종료(Ctrl+C).
Expected: users 테이블 생성, Flyway 적용 로그 출력

- [ ] **Step 3: 테이블 검증**

Run: `docker exec gildongmu-postgres psql -U gildongmu -d gildongmu -c "\d users"`
Expected: 위 컬럼/제약(uq_users_provider) 출력

- [ ] **Step 4: 커밋 지점 (수동)** — `Add: users 테이블 마이그레이션`

---

# Phase 1 — User 도메인

### Task 5: Provider enum + Converter

**Files:**
- Create: `src/main/java/com/gildongmu/gildongmu_backend/user/entity/Provider.java`
- Create: `src/main/java/com/gildongmu/gildongmu_backend/user/entity/ProviderConverter.java`

- [ ] **Step 1: Provider enum 작성**

```java
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
```

- [ ] **Step 2: AttributeConverter 작성**

```java
package com.gildongmu.gildongmu_backend.user.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ProviderConverter implements AttributeConverter<Provider, String> {

    @Override
    public String convertToDatabaseColumn(Provider provider) {
        return provider == null ? null : provider.getCode();
    }

    @Override
    public Provider convertToEntityAttribute(String code) {
        return code == null ? null : Provider.from(code);
    }
}
```

> 참고: `ErrorCode.UNSUPPORTED_PROVIDER`와 `CustomException`은 Task 8에서 생성한다. 순서상 Task 8을 먼저 적용하거나, 컴파일 에러가 나면 Task 8 완료 후 빌드한다.

- [ ] **Step 3: 커밋 지점 (수동)** — `Add: Provider enum 및 컨버터`

---

### Task 6: User 엔티티

**Files:**
- Create: `src/main/java/com/gildongmu/gildongmu_backend/user/entity/User.java`

- [ ] **Step 1: 엔티티 작성**

```java
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

    @Column(nullable = false, length = 50)
    private String nickname;

    @Column(name = "pref_radius_km", nullable = false)
    private int prefRadiusKm;

    @Column(name = "alarm_enabled", nullable = false)
    private boolean alarmEnabled;

    @Column(name = "alarm_cooldown_min", nullable = false)
    private int alarmCooldownMin;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private User(Provider provider, String providerId, String email, String nickname) {
        this.provider = provider;
        this.providerId = providerId;
        this.email = email;
        this.nickname = nickname;
        this.prefRadiusKm = 5;
        this.alarmEnabled = true;
        this.alarmCooldownMin = 30;
        this.createdAt = LocalDateTime.now();
    }

    public static User register(Provider provider, String providerId, String email, String nickname) {
        return new User(provider, providerId, email, nickname);
    }
}
```

- [ ] **Step 2: 커밋 지점 (수동)** — `Add: User 엔티티`

---

### Task 7: UserRepository (+ 조회 테스트)

**Files:**
- Create: `src/main/java/com/gildongmu/gildongmu_backend/user/repository/UserRepository.java`
- Test: `src/test/java/com/gildongmu/gildongmu_backend/user/repository/UserRepositoryTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`@DataJpaTest`는 기본적으로 임베디드 DB를 쓰려 하므로, 실제 PostgreSQL을 쓰도록 `@AutoConfigureTestDatabase(replace = NONE)`를 명시한다. (Docker postgres + local 프로파일 필요)

```java
package com.gildongmu.gildongmu_backend.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.gildongmu.gildongmu_backend.user.entity.Provider;
import com.gildongmu.gildongmu_backend.user.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("local")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void provider와_providerId로_회원을_조회한다() {
        User saved = userRepository.save(
                User.register(Provider.KAKAO, "kakao-123", "a@b.com", "tester"));

        var found = userRepository.findByProviderAndProviderId(Provider.KAKAO, "kakao-123");

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(saved.getId());
    }

    @Test
    void 존재하지_않으면_빈_Optional을_반환한다() {
        var found = userRepository.findByProviderAndProviderId(Provider.NAVER, "none");

        assertThat(found).isEmpty();
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*UserRepositoryTest"`
Expected: FAIL — `UserRepository` / `findByProviderAndProviderId` 미존재로 컴파일 실패

- [ ] **Step 3: Repository 작성**

```java
package com.gildongmu.gildongmu_backend.user.repository;

import java.util.Optional;
import com.gildongmu.gildongmu_backend.user.entity.Provider;
import com.gildongmu.gildongmu_backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByProviderAndProviderId(Provider provider, String providerId);
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*UserRepositoryTest"`
Expected: PASS (Docker postgres 기동 상태여야 함)

- [ ] **Step 5: 커밋 지점 (수동)** — `Add: UserRepository 및 조회 테스트`

---

# Phase 2 — 예외 처리 (global)

### Task 8: ErrorCode / CustomException / 핸들러

**Files:**
- Create: `global/exception/ErrorCode.java`
- Create: `global/exception/CustomException.java`
- Create: `global/exception/ErrorResponse.java`
- Create: `global/exception/GlobalExceptionHandler.java`

- [ ] **Step 1: ErrorCode 작성**

```java
package com.gildongmu.gildongmu_backend.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 소셜 토큰입니다."),
    UNSUPPORTED_PROVIDER(HttpStatus.BAD_REQUEST, "지원하지 않는 소셜 제공자입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 리프레시 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 토큰입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");

    private final HttpStatus status;
    private final String message;
}
```

- [ ] **Step 2: CustomException 작성**

```java
package com.gildongmu.gildongmu_backend.global.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {

    private final ErrorCode errorCode;

    public CustomException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
```

- [ ] **Step 3: ErrorResponse 작성**

```java
package com.gildongmu.gildongmu_backend.global.exception;

public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.name(), errorCode.getMessage());
    }
}
```

- [ ] **Step 4: GlobalExceptionHandler 작성**

```java
package com.gildongmu.gildongmu_backend.global.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("CustomException: {} - {}", errorCode.name(), errorCode.getMessage());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode));
    }
}
```

- [ ] **Step 5: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL (Task 5의 Provider도 함께 컴파일됨)

- [ ] **Step 6: 커밋 지점 (수동)** — `Add: 공통 예외 처리 구조`

---

# Phase 3 — JWT

### Task 9: JwtProvider (+ 발급/검증 테스트)

**Files:**
- Create: `auth/jwt/JwtProvider.java`
- Test: `src/test/java/com/gildongmu/gildongmu_backend/auth/jwt/JwtProviderTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

```java
package com.gildongmu.gildongmu_backend.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gildongmu.gildongmu_backend.global.exception.CustomException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(
                "test-secret-key-test-secret-key-test-secret-key-0123456789",
                1800000L,
                2592000000L);
    }

    @Test
    void accessToken을_발급하고_userId를_파싱한다() {
        String token = jwtProvider.createAccessToken(42L);

        Long userId = jwtProvider.parseUserId(token);

        assertThat(userId).isEqualTo(42L);
    }

    @Test
    void 위조된_토큰은_예외를_던진다() {
        assertThatThrownBy(() -> jwtProvider.parseUserId("tampered.token.value"))
                .isInstanceOf(CustomException.class);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*JwtProviderTest"`
Expected: FAIL — `JwtProvider` 미존재 컴파일 실패

- [ ] **Step 3: JwtProvider 구현**

```java
package com.gildongmu.gildongmu_backend.auth.jwt;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import com.gildongmu.gildongmu_backend.global.exception.CustomException;
import com.gildongmu.gildongmu_backend.global.exception.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessTokenValidityMs;
    private final long refreshTokenValidityMs;

    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-validity-ms}") long accessTokenValidityMs,
            @Value("${jwt.refresh-token-validity-ms}") long refreshTokenValidityMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenValidityMs = accessTokenValidityMs;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public String createAccessToken(Long userId) {
        return createToken(userId, accessTokenValidityMs);
    }

    public String createRefreshToken(Long userId) {
        return createToken(userId, refreshTokenValidityMs);
    }

    private String createToken(Long userId, long validityMs) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMs);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Long parseUserId(String token) {
        try {
            String subject = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
            return Long.valueOf(subject);
        } catch (ExpiredJwtException e) {
            throw new CustomException(ErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new CustomException(ErrorCode.INVALID_TOKEN);
        }
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*JwtProviderTest"`
Expected: PASS

- [ ] **Step 5: 커밋 지점 (수동)** — `Add: JWT 발급/검증 Provider`

---

### Task 10: JwtAuthentication + JwtAuthenticationFilter

**Files:**
- Create: `auth/jwt/JwtAuthentication.java`
- Create: `auth/jwt/JwtAuthenticationFilter.java`

- [ ] **Step 1: 인증 토큰 객체 작성**

`@AuthenticationPrincipal Long userId`로 꺼낼 수 있도록 principal에 userId를 담는다.

```java
package com.gildongmu.gildongmu_backend.auth.jwt;

import java.util.Collections;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public class JwtAuthentication extends AbstractAuthenticationToken {

    private final Long userId;

    public JwtAuthentication(Long userId) {
        super(Collections.emptyList());
        this.userId = userId;
        setAuthenticated(true);
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }

    @Override
    public Object getCredentials() {
        return null;
    }
}
```

- [ ] **Step 2: 필터 작성**

토큰이 없거나 유효하지 않으면 SecurityContext를 비운 채 통과시키고, 인가 단계에서 401/403을 처리하도록 한다.

```java
package com.gildongmu.gildongmu_backend.auth.jwt;

import java.io.IOException;
import com.gildongmu.gildongmu_backend.global.exception.CustomException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Long userId = jwtProvider.parseUserId(token);
                SecurityContextHolder.getContext()
                        .setAuthentication(new JwtAuthentication(userId));
            } catch (CustomException e) {
                log.debug("JWT 인증 실패: {}", e.getErrorCode().name());
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
```

- [ ] **Step 3: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 커밋 지점 (수동)** — `Add: JWT 인증 필터`

---

# Phase 4 — Refresh Token (Redis)

### Task 11: RefreshTokenService (+ 통합 테스트)

**Files:**
- Create: `auth/service/RefreshTokenService.java`
- Test: `src/test/java/com/gildongmu/gildongmu_backend/auth/service/RefreshTokenServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

실제 Redis(Docker) 사용. `@SpringBootTest` + local 프로파일.

```java
package com.gildongmu.gildongmu_backend.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("local")
class RefreshTokenServiceTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void 저장한_리프레시토큰을_검증하고_삭제한다() {
        Long userId = 777L;
        refreshTokenService.save(userId, "refresh-abc");

        assertThat(refreshTokenService.isValid(userId, "refresh-abc")).isTrue();
        assertThat(refreshTokenService.isValid(userId, "wrong")).isFalse();

        refreshTokenService.delete(userId);
        assertThat(refreshTokenService.isValid(userId, "refresh-abc")).isFalse();

        redisTemplate.delete("refresh:" + userId);
    }
}
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*RefreshTokenServiceTest"`
Expected: FAIL — `RefreshTokenService` 미존재

- [ ] **Step 3: RefreshTokenService 구현**

```java
package com.gildongmu.gildongmu_backend.auth.service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;
    private final long refreshTokenValidityMs;

    public RefreshTokenService(
            StringRedisTemplate redisTemplate,
            @Value("${jwt.refresh-token-validity-ms}") long refreshTokenValidityMs) {
        this.redisTemplate = redisTemplate;
        this.refreshTokenValidityMs = refreshTokenValidityMs;
    }

    public void save(Long userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                key(userId),
                refreshToken,
                Duration.ofMillis(refreshTokenValidityMs).toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public boolean isValid(Long userId, String refreshToken) {
        String stored = redisTemplate.opsForValue().get(key(userId));
        return stored != null && stored.equals(refreshToken);
    }

    public void delete(Long userId) {
        redisTemplate.delete(key(userId));
    }

    private String key(Long userId) {
        return KEY_PREFIX + userId;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*RefreshTokenServiceTest"`
Expected: PASS (Docker redis 기동 상태)

- [ ] **Step 5: 커밋 지점 (수동)** — `Add: Redis 기반 RefreshTokenService`

---

# Phase 5 — OAuth 클라이언트

### Task 12: OAuthUserInfo + OAuthClient 인터페이스

**Files:**
- Create: `auth/oauth/OAuthUserInfo.java`
- Create: `auth/oauth/OAuthClient.java`

- [ ] **Step 1: OAuthUserInfo 작성**

```java
package com.gildongmu.gildongmu_backend.auth.oauth;

import com.gildongmu.gildongmu_backend.user.entity.Provider;

public record OAuthUserInfo(
        Provider provider,
        String providerId,
        String email,
        String nickname) {
}
```

- [ ] **Step 2: OAuthClient 인터페이스 작성**

```java
package com.gildongmu.gildongmu_backend.auth.oauth;

import com.gildongmu.gildongmu_backend.user.entity.Provider;

public interface OAuthClient {

    Provider supports();

    OAuthUserInfo getUserInfo(String accessToken);
}
```

- [ ] **Step 3: 커밋 지점 (수동)** — `Add: OAuthClient 인터페이스`

---

### Task 13: KakaoOAuthClient

**Files:**
- Create: `auth/oauth/KakaoOAuthClient.java`

카카오 응답: `id`(Long), `kakao_account.email`(선택), `kakao_account.profile.nickname`.

- [ ] **Step 1: 구현 작성**

```java
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
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋 지점 (수동)** — `Add: 카카오 OAuth 클라이언트`

---

### Task 14: NaverOAuthClient

**Files:**
- Create: `auth/oauth/NaverOAuthClient.java`

네이버 응답: `response.id`(String), `response.email`, `response.nickname`.

- [ ] **Step 1: 구현 작성**

```java
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
            String nickname = (String) response.getOrDefault("nickname", "사용자");

            return new OAuthUserInfo(Provider.NAVER, providerId, email, nickname);
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("네이버 사용자 정보 조회 실패", e);
            throw new CustomException(ErrorCode.INVALID_SOCIAL_TOKEN);
        }
    }
}
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋 지점 (수동)** — `Add: 네이버 OAuth 클라이언트`

---

# Phase 6 — Auth 서비스 / 컨트롤러 / 보안 설정

### Task 15: DTO 4종

**Files:**
- Create: `auth/dto/SocialLoginRequest.java`
- Create: `auth/dto/ReissueRequest.java`
- Create: `auth/dto/LoginResponse.java`
- Create: `auth/dto/TokenResponse.java`

- [ ] **Step 1: DTO 작성**

```java
package com.gildongmu.gildongmu_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record SocialLoginRequest(@NotBlank String accessToken) {
}
```

```java
package com.gildongmu.gildongmu_backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ReissueRequest(@NotBlank String refreshToken) {
}
```

```java
package com.gildongmu.gildongmu_backend.auth.dto;

public record LoginResponse(String accessToken, String refreshToken, boolean isNewUser) {
}
```

```java
package com.gildongmu.gildongmu_backend.auth.dto;

public record TokenResponse(String accessToken, String refreshToken) {
}
```

- [ ] **Step 2: 커밋 지점 (수동)** — `Add: auth DTO`

---

### Task 16: AuthService (+ 로그인/재발급 테스트, OAuthClient mock)

**Files:**
- Create: `auth/service/AuthService.java`
- Test: `src/test/java/com/gildongmu/gildongmu_backend/auth/service/AuthServiceTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

OAuthClient는 mock, Repository는 실제(@DataJpaTest 아닌 단위 mock)로 한다. 여기서는 Mockito로 협력자를 모두 mock 해 순수 로직을 검증한다.

```java
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
```

- [ ] **Step 2: 테스트 실패 확인**

Run: `./gradlew test --tests "*AuthServiceTest"`
Expected: FAIL — `AuthService` 미존재

- [ ] **Step 3: AuthService 구현**

```java
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
                            userInfo.email(),
                            userInfo.nickname()));
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
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --tests "*AuthServiceTest"`
Expected: PASS

- [ ] **Step 5: 커밋 지점 (수동)** — `Add: AuthService 로그인/재발급/로그아웃`

---

### Task 17: AuthController

**Files:**
- Create: `auth/controller/AuthController.java`

- [ ] **Step 1: 컨트롤러 작성**

```java
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
```

- [ ] **Step 2: 컴파일 확인**

Run: `./gradlew compileJava`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 커밋 지점 (수동)** — `Add: AuthController`

---

### Task 18: SecurityConfig

**Files:**
- Create: `global/config/SecurityConfig.java`

- [ ] **Step 1: 설정 작성**

```java
package com.gildongmu.gildongmu_backend.global.config;

import com.gildongmu.gildongmu_backend.auth.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/reissue").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
```

- [ ] **Step 2: 전체 빌드 + 컨텍스트 로드 확인**

Run: `./gradlew test --tests "*GildongmuBackendApplicationTests"`
Expected: PASS (Docker postgres/redis 기동 상태에서 컨텍스트 정상 로드)

- [ ] **Step 3: 커밋 지점 (수동)** — `Add: Spring Security 설정`

---

### Task 19: 엔드투엔드 수동 검증

**Files:** 없음 (수동 검증)

- [ ] **Step 1: 앱 기동**

Run: `JWT_SECRET=local-dev-secret-please-change-this-to-32bytes-or-more-0123456789 ./gradlew bootRun`
Expected: 정상 기동, 에러 없음

- [ ] **Step 2: 보호 엔드포인트 인증 차단 확인**

Run: `curl -i -X POST http://localhost:8080/api/v1/auth/logout`
Expected: 401 또는 403 (토큰 없음)

- [ ] **Step 3: 잘못된 소셜 토큰 처리 확인**

Run: `curl -i -X POST http://localhost:8080/api/v1/auth/login/kakao -H "Content-Type: application/json" -d '{"accessToken":"invalid"}'`
Expected: 401 + body `{"code":"INVALID_SOCIAL_TOKEN", ...}`

- [ ] **Step 4: 실제 토큰 검증 (선택)**

카카오/네이버 SDK로 발급받은 실제 access token이 있으면 동일 요청으로 200 + `accessToken/refreshToken/isNewUser` 응답 확인.

- [ ] **Step 5: 커밋 지점 (수동)** — 검증 완료 메모

---

## 부록: 실행 전 체크리스트
- Docker 컨테이너(postgres, redis) 기동 상태
- `application-local.yml` 작성 + `jwt.secret` 32바이트 이상
- 환경변수 `JWT_SECRET` 또는 local 프로파일 secret 설정
- 카카오 개발자 콘솔 Android 키 해시 등록(프론트 연동 시)
- 네이버 로그인 API 이용 신청/검수(프론트 연동 시)
