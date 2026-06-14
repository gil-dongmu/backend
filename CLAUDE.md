# 길동무 백엔드 (Gildongmu Backend)

## 프로젝트 개요
한국관광공사 관광데이터 활용 공모전 출품작.
운전 중 주변 축제를 TTS로 알려주고 경로를 변경할 수 있는 드라이빙 큐레이션 앱의 백엔드 서버.
지방 축제 활성화를 통한 지방 경제 트래픽 창출이 핵심 목표.

## 기술 스택
- **언어/프레임워크**: Java 21, Spring Boot 4.0.x
- **DB**: PostgreSQL + PostGIS (공간 데이터), AWS RDS
- **캐시**: Redis (EC2 내장) — Refresh Token, 축제 상세, 날씨, 주변 관광지 캐시
- **ORM**: Spring Data JPA + Hibernate Spatial (PostGIS Point 타입 매핑)
- **인증**: Spring Security + JWT (Access Token 30분, Refresh Token 30일)
- **API 문서**: SpringDoc OpenAPI (Swagger UI)
- **빌드**: Gradle
- **배포**: AWS EC2, AWS RDS
- **앱**: Flutter (Android)
- **네비**: 카카오 길찾기 SDK (인앱)

## 패키지 구조 (Package by Feature)
```
com.gildongmu
├── auth          ← 소셜 로그인(카카오/네이버) + JWT
├── festival      ← 축제 검색 + 새벽 배치 동기화
├── drive         ← 주행 세션 관리
├── recommend     ← 추천 로직 + TTS 알림 로그
├── user          ← 회원 + 선호 카테고리
├── bookmark      ← 찜하기
├── review        ← 리뷰 + 별점
├── visit         ← 방문 기록
└── global        ← config / exception / util
```

각 도메인은 controller / service / repository / entity / dto 하위 패키지 보유.

## 아키텍처
Layered Architecture (도메인 기반 패키지)
```
Controller → Service → Repository → DB(PostgreSQL + Redis)
```
- Controller: 요청/응답 처리만. 비즈니스 로직 없음
- Service: 비즈니스 로직 담당. @Transactional 적용
- Repository: DB 접근만. PostGIS 네이티브 쿼리 사용

## DB 테이블 (8개)
| 테이블 | 역할 |
|--------|------|
| users | 회원 정보 + 알림 설정 (profile_image 없음) |
| user_category_prefs | 선호 축제 유형 다중 선택 (EV01/EV02/EV03) |
| festivals | TourAPI 축제 원본 데이터 (PostGIS location 컬럼) |
| drive_sessions | 주행 세션 (GPS 좌표 저장 안 함, complete_type/accepted_count 없음) |
| recommend_logs | TTS 알림 기록 (방문 전환율 측정) |
| bookmarks | 찜한 축제 |
| reviews | 리뷰 + 별점 (1~5) |
| visit_logs | 방문 기록 (visit_type: auto/manual) |

상세 스키마: @docs/erd.md

## Redis 사용처
| 키 패턴 | 용도 | TTL |
|---------|------|-----|
| refresh:{userId} | Refresh Token | 30일 |
| festival-common:{contentId} | detailCommon2 캐시 | 1시간 |
| festival-intro:{contentId} | detailIntro2 캐시 | 1시간 |
| today-festivals:{lat},{lng} | 오늘 축제 목록 | 6시간 |
| weather:{lat},{lng} | 날씨 정보 | 10분 |
| nearby:{lat},{lng} | 주변 관광지 (좌표 반올림 격자) | 10분 |

## 핵심 비즈니스 규칙
1. TourAPI 원천 데이터 변형 금지 — 필드 값 그대로 저장
2. GPS 좌표 서버 저장 금지 — 위치기반서비스법 준수
3. TourAPI 호출은 백엔드에서만 — API 키 보호
4. 방문 기록(VISIT_LOGS) 있어야 리뷰 작성 가능 — 백엔드 로직으로 검증
5. ST_MakePoint(mapx, mapy) — 경도 먼저, 위도 나중 순서 필수
6. Refresh Token은 DB 저장 안 함 — Redis TTL로만 관리
7. 인덱스는 추후 쿼리 성능 측정 후 직접 추가 (초기 DDL에 포함 안 함)

## 소셜 로그인 구현 주의사항
- 카카오: email은 선택 동의 항목 → null 가능, null 체크 필수
- 카카오 provider_id: Long 타입으로 응답 → String 변환 후 저장
- 네이버 provider_id: String 타입으로 응답 → 그대로 저장
- provider + provider_id 조합으로 기존/신규 회원 판별
- 신규 회원이면 응답에 isNewUser=true 포함 → 프론트가 온보딩 화면 분기
- 카카오/네이버 자체 Access Token은 로그인 검증 1회용. 이후 우리 JWT만 사용
- 카카오 개발자 콘솔: Android 키 해시(디버그+릴리즈) 등록 필수
- 네이버 로그인: API 이용 신청 후 검수 필요 (일정 고려)

## 추천 로직 (운전 중 TTS 알림)
```
1. 앱이 현재 GPS 좌표 전송 (서버 저장 안 함)
2. ST_DWithin으로 반경 내 + 오늘 열리는 축제 검색
3. 사용자 선호 카테고리 필터링 (user_category_prefs)
4. 거리순 정렬 후 1개만 선택
5. 쿨다운 확인 (recommend_logs에서 최근 알림 시각 체크)
6. 앱에 반환 → TTS 알림 → 수락 시 RECOMMEND_LOGS 기록
```

## 경로 상 축제 미리보기 (출발 전)
```
1. 카카오 길찾기 SDK가 경로 좌표 배열 반환
2. 앱이 좌표 배열을 백엔드에 전송
3. ST_MakeLine으로 경로 선분 생성
4. ST_DWithin(10km)으로 경로 주변 오늘 축제 검색
5. 결과를 지도 위 마커로 표시
```
판단 기준은 목적지가 아니라 경로 선분 전체에서의 거리.

## 외부 API
| API | 용도 | 호출 주체 |
|-----|------|---------|
| TourAPI searchFestival2 | 축제 목록 배치 동기화 (새벽 3시) | 백엔드 |
| TourAPI detailCommon2 | 축제 개요(overview) | 백엔드 (상세 탭 시, 캐싱) |
| TourAPI detailIntro2 | 공연시간·요금·프로그램 | 백엔드 (상세 탭 시, 캐싱) |
| TourAPI locationBasedList2 | 주변 관광지·체험관광 | 백엔드 (실시간, 캐싱) |
| 기상청 단기예보 API | 현재 날씨 | 백엔드 (캐싱) |
| 카카오/네이버 OAuth | 소셜 로그인 검증 | 백엔드 |

TourAPI 오퍼레이션 일일 1,000건 제한 → Redis 캐싱 필수.

## 환경 설정
- 로컬: application-local.yml (gitignore)
- 운영: application-prod.yml + AWS Parameter Store
- ddl-auto: validate (로컬) / none (운영)
- Flyway로 스키마 버전 관리

## 구현 현황
- [x] Spring Boot 초기 세팅 + 클린 빌드
- [x] DB 스키마 설계 (ERD 확정)
- [ ] DB 스키마 적용 (PostGIS 포함)
- [ ] JPA 엔티티 작성 (Hibernate Spatial)
- [ ] Redis 설정
- [ ] 소셜 로그인 (카카오/네이버 OAuth2)
- [ ] JWT 필터 (Access Token 검증)
- [ ] TourAPI 배치 동기화 (searchFestival2)
- [ ] 위치기반 축제 검색 API
- [ ] 경로 상 축제 미리보기 API
- [ ] 추천 로직 + 쿨다운
- [ ] 주행 세션 API
- [ ] 찜 / 리뷰 / 방문 기록 API
- [ ] Swagger 문서화
- [ ] EC2 배포

## 참고 문서
- @docs/erd.md         ERD 다이어그램 + 테이블 설명
- @docs/api-spec.md    API 명세서
- @docs/architecture.md 시스템 아키텍처
