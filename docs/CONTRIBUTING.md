# 길동무 Git 커밋 및 브랜치 전략 가이드

## 목차
- [커밋 메시지 규칙](#커밋-메시지-규칙)
- [브랜치 전략](#브랜치-전략)
---

## 커밋 메시지 규칙

### 형식

```
type: 제목 

본문 (선택)
```

### type 종류

| type | 설명 | 예시 |
|------|------|------|
| `feat` | 새로운 기능 추가 | `feat: 위치기반 축제 검색 API 추가 ` |
| `fix` | 버그 수정 | `fix: 반경 계산 오류 수정 ` |
| `docs` | 문서 수정 | `docs: API 명세서 엔드포인트 추가` |
| `refactor` | 코드 리팩토링 (기능 변화 없음) | `refactor: 축제 추천 로직 서비스 레이어 분리` |
| `test` | 테스트 코드 추가/수정 | `test: 공간 검색 쿼리 단위 테스트 추가` |
| `chore` | 빌드 설정, 의존성 변경 | `chore: PostGIS Hibernate Spatial 의존성 추가` |
| `style` | 코드 포맷, 세미콜론 등 (기능 변화 없음) | `style: 들여쓰기 및 공백 정리` |
| `perf` | 성능 개선 | `perf: 공간 인덱스 쿼리 최적화` |
| `ci` | CI/CD 설정 변경 | `ci: GitHub Actions 배포 워크플로우 추가` |
| `remove` | 파일/코드 삭제 | `remove: 사용하지 않는 레거시 컨트롤러 삭제` |

### 규칙
- 제목은 **50자 이내**로 작성
- 제목 끝맺음은 **명사형**으로 작성
- 제목 끝에 마침표 **금지**
- 본문은 **무엇을, 왜** 변경했는지 작성

### 예시

```
feat: 소셜 로그인(네이버/카카오) OAuth2 연동 

네이버와 카카오 소셜 로그인을 추가했습니다.
provider, provider_id 기반으로 회원을 식별하며
이메일이 없는 경우(카카오 미동의)도 처리합니다.
```

```
fix: TTS 알림 쿨다운 적용 안 되는 버그 수정 
```

```
chore: Spring Boot 4.0 프로젝트 초기 세팅
```

---

## 브랜치 전략

### 브랜치 종류

| 브랜치 | 용도 | 규칙 |
|--------|------|------|
| `main` | 배포용 | 직접 push 금지, PR로만 머지 |
| `develop` | 개발 통합 브랜치 | 기능 개발 완료 후 PR |
| `feat/기능명` | 새 기능 개발 | develop에서 분기 |
| `fix/버그명` | 버그 수정 | develop에서 분기 |
| `refactor/내용` | 리팩토링 | develop에서 분기 |
| `docs/내용` | 문서 작업 | develop에서 분기 |

### 브랜치 네이밍 예시

```
feat/festival-search-api
feat/social-login
feat/tts-alarm
fix/spatial-query-bug
refactor/recommend-service
docs/api-spec-update
```

### 흐름

```
main
 └── develop
       ├── feat/social-login
       ├── feat/festival-search-api
       ├── feat/tts-alarm
       └── fix/spatial-query-bug
```

```
feat/festival-search-api
        ↓ PR → 코드 리뷰 → 머지
     develop
        ↓ PR → 최종 확인 → 머지 (배포 시)
      main
```

### 브랜치 생성 방법

```bash
# develop에서 pull을 떙긴뒤 새 브랜치 만들어야함
git checkout develop
git pull origin develop
git checkout -b feat/festival-search-api
```
---

### 추가 규칙

* PR 머지 후 브랜치는 **GitHub 설정(Delete branch after merge)** 으로 원격 브랜치 자동 삭제
* 머지 완료된 브랜치는 재사용하지 않고 develop 기준으로 새로 생성

### 머지 이후 브랜치 정리

```bash
# 원격 삭제된 브랜치 정보 로컬 반영
git fetch --prune

# 사용 완료한 로컬 브랜치 삭제
git branch -d feat/festival-search-api
```

---

