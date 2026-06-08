# 길동무 ERD 설명

> 총 8개 테이블 · PostgreSQL + PostGIS · Redis 별도 운용

---

## 테이블 관계 요약

```
USERS (1)
  ├── USER_CATEGORY_PREFS (N)   ← 선호 카테고리 다중 선택
  ├── DRIVE_SESSIONS (N)
  │     └── RECOMMEND_LOGS (N) ← N:1 → FESTIVALS
  ├── BOOKMARKS (N)             ← N:1 → FESTIVALS
  ├── REVIEWS (N)               ← N:1 → FESTIVALS
  └── VISIT_LOGS (N)            ← N:1 → FESTIVALS

N:M 관계 (중간 테이블로 해소)
  USERS ─── BOOKMARKS  ─── FESTIVALS
  USERS ─── REVIEWS    ─── FESTIVALS
  USERS ─── VISIT_LOGS ─── FESTIVALS
```

---

## 테이블 상세 설명

### 1. USERS — 회원 정보

소셜 로그인(카카오/네이버)으로만 가입하는 회원 테이블.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| user_id | bigint PK | 내부 식별자 |
| provider | varchar(10) | 소셜 종류. 'naver' / 'kakao' |
| provider_id | varchar(100) | 소셜 고유 회원번호 |
| email | varchar(255) | 이메일. 카카오 미동의 시 NULL |
| nickname | varchar(50) | 닉네임 |
| pref_radius_km | int | 알림 반경(km). 기본 5km |
| alarm_enabled | boolean | 알림 전체 on/off. |
| alarm_cooldown_min | int | 재알림 최소 간격 |
| created_at | timestamp | 가입 시각 |

**제약조건**
- `UNIQUE(provider, provider_id)` — 소셜+ID 조합이 식별자

---

### 2. USER_CATEGORY_PREFS — 선호 축제 유형

 다중 선택한 축제 취향을 저장하는 테이블.


| 컬럼 | 타입 | 설명 |
|------|------|------|
| pref_id | bigint PK | 식별자 |
| user_id | bigint FK | 누구의 선호인지 |
| category | varchar(10) | EV01=축제 / EV02=공연 / EV03=행사 |
| created_at | timestamp | 설정 시각 |

**제약조건**
- `UNIQUE(user_id, category)` — 같은 카테고리 중복 방지

---

### 3. FESTIVALS — 축제 데이터


배치로 자동 동기화. 

| 컬럼 | 타입 | 설명 |
|------|------|------|
| festival_id | bigint PK | 내부 식별자 |
| content_id | varchar(20) UNIQUE | TourAPI contentid. UPSERT 기준 |
| title | varchar(200) | 축제명 |
| addr1 | varchar(300) | 주소 |
| addr2 | varchar(200) | 상세주소 |
| location | GEOGRAPHY(POINT,4326) | 위경도. ST_MakePoint(mapx, mapy) — 경도 먼저 |
| event_start_date | date | 행사 시작일. eventstartdate 변환 |
| event_end_date | date | 행사 종료일. eventenddate 변환 |
| first_image | varchar(500) | 대표 이미지 URL (원본 500x333) |
| first_image2 | varchar(500) | 썸네일 이미지 URL (150x100) |
| tel | varchar(100) | 전화번호 |
| lcls_systm2 | varchar(10) | 중분류. EV01/EV02/EV03. 카테고리 필터용 |
| lcls_systm3 | varchar(15) | 소분류. 세부 축제 종류 |
| l_dong_regn_cd | varchar(10) | 법정동 시도 코드. 지역 필터 확장용 |
| l_dong_signgu_cd | varchar(10) | 법정동 시군구 코드. 지역 필터 확장용 |
| modified_time | varchar(14) | API 수정일시. 증분 동기화 기준 |
| synced_at | timestamp | 우리 DB 동기화 시각 |



---

### 4. DRIVE_SESSIONS — 주행 세션

내비 안내 시작부터 종료까지 한 번의 주행을 기록.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| session_id | bigint PK | 세션 식별자 |
| user_id | bigint FK | 누구의 주행인지 |
| dest_name | varchar(200) | 목적지 이름. GPS 좌표 저장 안 함 |
| origin_name | varchar(200) | 출발지 이름. GPS 좌표 저장 안 함 |
| started_at | timestamp | 안내 시작 시각 |
| ended_at | timestamp | 안내 종료 시각. NULL=주행 중 |
| is_completed | boolean | 정상 도착 여부 |
---

### 5. RECOMMEND_LOGS — TTS 알림 기록

운전 중 TTS로 알림을 보낼 때마다 기록하는 테이블.
방문 전환율 측정 근거.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| log_id | bigint PK | 로그 식별자 |
| session_id | bigint FK | 어느 주행의 알림인지 |
| festival_id | bigint FK | 어느 축제를 알렸는지 |
| suggested_at | timestamp | 알림 시각. 쿨다운 계산 기준 |
| is_accepted | boolean | 수락 여부. 전환율 핵심 컬럼 |
| accept_type | varchar(15) | 'waypoint'(경유지) / 'destination'(목적지변경) / NULL(거절) |


---

### 6. BOOKMARKS — 찜한 축제

사용자가 하트/북마크 버튼을 눌러 저장한 축제.
USERS와 FESTIVALS의 N:M 관계를 해소하는 중간 테이블.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| bookmark_id | bigint PK | 식별자 |
| user_id | bigint FK | 누가 찜했는지 |
| festival_id | bigint FK | 어떤 축제인지 |
| created_at | timestamp | 찜한 시각 |

**제약조건**
- `UNIQUE(user_id, festival_id)` — 같은 축제 중복 찜 방지
---

### 7. REVIEWS — 리뷰 + 별점

축제를 방문한 사용자가 남기는 리뷰 테이블.
**방문 기록(VISIT_LOGS)이 있어야 작성 가능** 

| 컬럼 | 타입 | 설명 |
|------|------|------|
| review_id | bigint PK | 식별자 |
| user_id | bigint FK | 누가 작성했는지 |
| festival_id | bigint FK | 어느 축제 리뷰인지 |
| rating | smallint | 별점 1~5. CHECK(rating BETWEEN 1 AND 5) |
| content | varchar(500) | 리뷰 텍스트. NULL 허용 (별점만도 가능) |
| created_at | timestamp | 작성 시각 |

**제약조건**
- `UNIQUE(user_id, festival_id)` — 축제당 리뷰 1개

---

### 8. VISIT_LOGS — 방문 기록

실제로 축제에 방문했을 때 기록되는 테이블.
리뷰 작성의 선행 조건. 두 가지 방식으로 기록.

| 컬럼 | 타입 | 설명 |
|------|------|------|
| visit_id | bigint PK | 식별자 |
| user_id | bigint FK | 누가 방문했는지 |
| festival_id | bigint FK | 어느 축제인지 |
| visit_type | varchar(10) | 'auto'(네비 통해 자동) / 'manual'(직접 기록) |
| visited_at | timestamp | 방문 시각 |

**제약조건**
- `UNIQUE(user_id, festival_id)` — 중복 방문 기록 방지

---

