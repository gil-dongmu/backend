# 소셜 로그인 (카카오 / 네이버) 설계 문서

> 작성일: 2026-06-14 · 대상: gildongmu-backend · 브랜치: feat/login

---

## 1. 목표 및 범위

### 목표
카카오·네이버 소셜 로그인을 구현한다. 모바일 앱(Flutter)이 소셜 SDK로 로그인 후
획득한 access token을 백엔드로 보내면, 백엔드는 그 토큰으로 사용자 정보를 조회해
회원을 매칭/가입시키고 자체 JWT(Access/Refresh)를 발급한다.

### 전제
- 프론트(Flutter)는 카카오/네이버 SDK가 이미 연동된 것으로 가정한다.
  백엔드는 앱이 보낸 access token을 받는 시점부터 책임진다.
- SDK용 API 키(카카오 네이티브 앱 키, 네이버 Client ID)는 직접 발급·관리한다.
- 토큰 전략은 Access JWT(30분) + Refresh Token(30일, Redis)로 한다.
- 로컬 개발환경은 Docker(docker-compose)로 PostgreSQL+PostGIS+Redis를 띄운다.

### 작업 범위 (auth + 직접 데이터 의존성)
포함:
- auth 도메인 전체 (OAuth 조회, JWT 발급/검증, 로그인/재발급/로그아웃)
- `User` 엔티티 + `Provider` enum
- `users` 테이블 Flyway 마이그레이션 1개
- Redis 설정, Security 설정, Docker 로컬 인프라

제외:
- festivals, drive_sessions 등 나머지 7개 테이블 및 관련 기능
- 게스트(비회원) 모드
- 구글 로그인

---

## 2. 전체 흐름 (앱 전용, 토큰 검증 방식)

```
[Flutter 앱]
 1. 유저가 "카카오/네이버로 시작하기" 탭
 2. SDK 로그인 → 소셜 access token 획득 (code→token 교환은 SDK가 수행)
 3. POST /api/v1/auth/login/{provider}  body: { "accessToken": "..." }

[백엔드]
 4. 받은 소셜 토큰으로 카카오/네이버 사용자 정보 조회 (RestClient)
    → OAuthUserInfo(provider, providerId, email, nickname)
 5. UNIQUE(provider, provider_id)로 기존 회원 판별
    - 존재: 로그인
    - 없음: USERS INSERT (자동 회원가입), isNewUser=true
 6. 자체 JWT 발급 (Access 30분 / Refresh 30일)
 7. Refresh Token을 Redis에 저장 (refresh:{userId}, TTL 30일)
 8. 응답: { accessToken, refreshToken, isNewUser }

[Flutter 앱]
 9. 우리 JWT를 secure storage에 저장
 10. 이후 모든 API 요청에 Authorization: Bearer <우리JWT> 첨부
```

핵심 경계: 카카오/네이버 access token은 4단계 사용자 조회 1회용. 이후 인증은 우리 JWT만 사용한다.

---

## 3. 패키지 구조

루트는 현재 코드 기준 `com.gildongmu.gildongmu_backend`를 유지한다.

```
com.gildongmu.gildongmu_backend
├── auth                         소셜 로그인 + JWT
│   ├── controller → AuthController
│   ├── service    → AuthService, RefreshTokenService(Redis)
│   ├── oauth      → OAuthClient(interface), KakaoOAuthClient, NaverOAuthClient, OAuthUserInfo
│   ├── jwt        → JwtProvider, JwtAuthenticationFilter
│   └── dto        → SocialLoginRequest, LoginResponse, ReissueRequest, TokenResponse
├── user
│   ├── entity     → User, Provider(enum), ProviderConverter
│   └── repository → UserRepository
└── global
    ├── config     → SecurityConfig, RedisConfig
    └── exception  → CustomException, ErrorCode, GlobalExceptionHandler
```

---

## 4. 핵심 로직

### 4-1. 소셜 로그인 `POST /api/v1/auth/login/{provider}`
```
AuthController.login(provider, SocialLoginRequest{accessToken})
 → AuthService.login(provider, accessToken)
   1) OAuthClient 선택 (Map<Provider, OAuthClient> 전략 패턴)
   2) client.getUserInfo(accessToken) → OAuthUserInfo
      실패 시 CustomException(INVALID_SOCIAL_TOKEN)
   3) UserRepository.findByProviderAndProviderId
      존재 → 로그인 / 없음 → User.register(...) 저장, isNewUser=true
   4) JwtProvider.createAccessToken/ createRefreshToken
   5) RefreshTokenService.save(userId, refreshToken)  Redis TTL 30일
   6) return LoginResponse(accessToken, refreshToken, isNewUser)
```

전략 패턴: `OAuthClient` 인터페이스에 카카오/네이버 구현체를 두고 `Map<Provider, OAuthClient>`로 분기.
구글 추가 시 구현체 하나만 추가하면 된다(OCP).

### 4-2. 토큰 재발급 `POST /api/v1/auth/reissue`
```
ReissueRequest{refreshToken}
 1) JwtProvider로 서명·유효성 검증
 2) Redis 저장값과 일치 확인 (불일치/없음 → INVALID_REFRESH_TOKEN)
 3) 새 Access + 새 Refresh 발급 (Refresh Rotation: 기존 폐기)
 4) Redis 갱신 → TokenResponse
```

### 4-3. 로그아웃 `POST /api/v1/auth/logout` (인증 필요)
```
SecurityContext에서 userId 추출 → RefreshTokenService.delete(userId)
 → Redis에서 Refresh 제거 (이후 재발급 불가)
```

### 4-4. 인증 필터
```
JwtAuthenticationFilter (OncePerRequestFilter)
 → Authorization: Bearer <우리JWT> 파싱
 → JwtProvider.validate() → userId 추출
 → SecurityContext에 Authentication 주입
 → 컨트롤러에서 @AuthenticationPrincipal로 userId 사용
```

---

## 5. 엔티티 설계

### User (테이블: users)
| 필드 | 타입 | 비고 |
|------|------|------|
| userId | Long PK auto | |
| provider | Provider enum | DB엔 'kakao'/'naver' 소문자 저장 (ProviderConverter) |
| providerId | String | 카카오 Long→String 변환, 네이버 String 그대로 |
| email | String nullable | 카카오 미동의 시 null |
| nickname | String | |
| prefRadiusKm | int | 기본 5 |
| alarmEnabled | boolean | 기본 true |
| alarmCooldownMin | int | |
| createdAt | LocalDateTime | |

- 제약: `UNIQUE(provider, provider_id)`
- 정적 팩토리: `User.register(provider, providerId, email, nickname)`

### Provider (enum)
- `KAKAO("kakao")`, `NAVER("naver")`
- `ProviderConverter`(AttributeConverter)로 DB 소문자 코드 ↔ enum 매핑

---

## 6. 예외 처리

CLAUDE.md "구체적 커스텀 예외" 규칙 준수.

```
ErrorCode (enum): code + HttpStatus + message
 ├── INVALID_SOCIAL_TOKEN   401  카카오/네이버 토큰 무효
 ├── UNSUPPORTED_PROVIDER    400  지원하지 않는 provider
 ├── INVALID_REFRESH_TOKEN   401  Redis 불일치/위조
 └── EXPIRED_TOKEN           401  JWT 만료
CustomException(ErrorCode) — RuntimeException 상속
GlobalExceptionHandler (@RestControllerAdvice) — 통일된 에러 응답
```

외부 연동(카카오/네이버 호출)과 분기마다 방어적 로그(info/error)를 삽입한다.

---

## 7. 보안 설정 (global/config/SecurityConfig)

```
SecurityFilterChain
 ├── 세션: STATELESS, csrf 비활성 (REST API)
 ├── permitAll: POST /api/v1/auth/login/**, /api/v1/auth/reissue, Swagger
 ├── 그 외: authenticated
 └── JwtAuthenticationFilter를 UsernamePasswordAuthenticationFilter 앞에 추가
```

---

## 8. 인프라 (이번에 같이 만드는 최소 부품)

```
docker-compose.yml      postgis/postgis 이미지 + redis 이미지
application.yml         공통 (jpa ddl-auto: validate, flyway enable)
application-local.yml   로컬 datasource/redis 접속 + 카카오/네이버 키 (.gitignore)
build.gradle            security, data-redis, jjwt, validation, flyway, postgresql 추가
db/migration/V1__create_users.sql   users 테이블 (Flyway)
```

Redis 키: `refresh:{userId}`, TTL 30일 (CLAUDE.md 규칙 — Refresh는 DB 저장 금지, Redis TTL로만 관리).

---

## 9. API 명세 (요약)

| Method | Path | 인증 | 설명 |
|--------|------|------|------|
| POST | `/api/v1/auth/login/{provider}` | ❌ | 소셜 토큰 → 로그인/가입 → JWT 발급 |
| POST | `/api/v1/auth/reissue` | ❌ | Refresh로 Access 재발급 (Rotation) |
| POST | `/api/v1/auth/logout` | ✅ | Redis Refresh 삭제 |

### 응답 예시 (로그인)
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "eyJ...",
  "isNewUser": true
}
```

---

## 10. 외부 API 참고

| API | 용도 | 인증 |
|-----|------|------|
| 카카오 `GET https://kapi.kakao.com/v2/user/me` | 사용자 정보 조회 | 유저 access token (Bearer) |
| 네이버 `GET https://openapi.naver.com/v1/nid/me` | 사용자 정보 조회 | 유저 access token (Bearer) |

- 카카오: provider_id는 Long 응답 → String 변환. email은 선택 동의 → null 가능.
- 네이버: provider_id는 String 응답 → 그대로 저장. API 이용 신청 후 검수 필요(일정 고려).
- 카카오 개발자 콘솔: Android 키 해시(디버그+릴리즈) 등록 필수.
