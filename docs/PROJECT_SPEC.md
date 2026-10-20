# Spendly MVP PRD 및 개발 설계서

## 제품 목표
- 개인의 수입·지출, 월 예산, 정기결제 예정일을 관리하는 모바일 우선 웹 서비스
- 1인 개발, 주 15~20시간, 6주(90~120시간)
- 프론트엔드·백엔드·인증·DB·테스트·배포 전 과정 경험

## MVP 범위
P0: 가입/로그인/로그아웃, 거래 CRUD, 카테고리, 월 예산, 대시보드, 거래 검색/필터
P1: 정기결제 등록/수정/일시정지, 예정 결제, 월별 통계, 반응형, 테스트/배포
P2: CSV 내보내기
제외: 금융기관 연동, OCR, AI 분석, 네이티브 앱, 다중 통화, 공동 가계부

## 화면
- /register, /login: 계정 등록·인증
- /dashboard: 당월 수입/지출/잔액, 예산 진행률, 최근 거래, 예정 결제
- /transactions: 검색·필터·정렬·페이지네이션
- /transactions/new, /transactions/:id/edit: 거래 폼
- /subscriptions: 구독 목록, 신규/수정/일시정지
- /analytics: 월별/카테고리별 집계
- /settings: 월 예산, 카테고리 관리, 계정

## 기술
React + TypeScript + Vite, TanStack Query, React Hook Form, Zod, Tailwind; Java 17 + Spring Boot + Spring Security + JPA; PostgreSQL, Flyway, Docker; JUnit, Vitest, Playwright.

## DB 논리 모델
users(id UUID PK, email UNIQUE, password_hash, created_at)
categories(id UUID PK, user_id FK, name, type INCOME|EXPENSE, archived, created_at; UNIQUE(user_id,type,name))
transactions(id UUID PK, user_id FK, category_id FK, type, amount NUMERIC(15,0), occurred_on DATE, description, memo, created_at, updated_at, version; CHECK(amount>0))
budgets(id UUID PK, user_id FK, month DATE, amount NUMERIC(15,0), UNIQUE(user_id,month); CHECK(amount>=0), month는 매월 1일
subscriptions(id UUID PK, user_id FK, category_id FK, name, amount NUMERIC(15,0), billing_day INT 1..31, start_date DATE, status ACTIVE|PAUSED, created_at, updated_at)

관계: users 1:N categories/transactions/budgets/subscriptions, categories 1:N transactions/subscriptions.

## 도메인 규칙
- 거래 금액은 양의 정수 원화, 수입/지출은 type으로 구분; 클라이언트 Number 안전 정수 범위 검증, 서버 BigDecimal 또는 Long으로 정확히 계산
- 거래 category는 동일 user 소유, type 일치; 거래가 있는 카테고리는 삭제 대신 archive
- 예산은 사용자·월당 하나, 소비 합계는 지출만 계산; 환불은 수입으로 기록(초기 단순화)
- 구독은 예정 정보만 저장하고 실제 거래는 자동 생성하지 않음. 이중 집계 방지
- billing_day=31인 달의 결제 예정일은 해당 월 마지막 날로 보정; PAUSED는 예정 결제에서 제외
- 모든 소유 리소스 접근은 인증 사용자 ID로 필터링, 클라이언트의 user_id 신뢰 금지
- 거래 수정은 version을 이용한 낙관적 잠금; 충돌은 409
- 날짜는 사용자 로컬 날짜(초기 Asia/Seoul), DB의 발생일은 DATE

## REST API
POST /api/auth/register; POST /api/auth/login; POST /api/auth/logout; GET /api/me
GET /api/categories; POST /api/categories; PATCH /api/categories/{id}; DELETE /api/categories/{id} (archive)
GET /api/transactions?month=YYYY-MM&type=&categoryId=&q=&page=&size=&sort=; POST /api/transactions; GET /api/transactions/{id}; PATCH /api/transactions/{id}; DELETE /api/transactions/{id}
GET /api/budgets/{month}; PUT /api/budgets/{month}
GET /api/subscriptions; POST /api/subscriptions; PATCH /api/subscriptions/{id}; DELETE /api/subscriptions/{id}; GET /api/subscriptions/upcoming?from=&to=
GET /api/dashboard?month=YYYY-MM; GET /api/analytics/categories?month=YYYY-MM; GET /api/analytics/monthly?from=&to=

거래 생성 예시: {"type":"EXPENSE","amount":12000,"categoryId":"uuid","occurredOn":"2026-10-20","description":"점심","memo":""}
거래 응답 예시: {"id":"uuid","type":"EXPENSE","amount":12000,"categoryId":"uuid","occurredOn":"2026-10-20","description":"점심","memo":"","version":0}
오류 형식: {"code":"VALIDATION_ERROR","message":"...","details":[]}; 400 검증, 401 미인증, 403 접근금지, 404 없는 리소스, 409 버전 충돌

## 보안
세션 쿠키 HttpOnly, Secure(운영), SameSite 정책; CSRF 방어 활성화; 비밀번호 해시; 로그인 속도 제한; CORS 출처 제한; 개인정보/인증값 로그 제외. 배포 시 프론트·API를 동일 사이트/리버스 프록시로 제공하면 쿠키 설정 단순화.

## 주차별 순서
1주: 레포/개발환경, ERD, Flyway, 공통 레이아웃, 카테고리·거래 API 기본, 첫 통합 호출
2주: 거래 CRUD 화면, 폼 검증, 검색/필터/페이징, Query 캐시 및 오류 상태
3주: 회원가입/로그인/세션/CSRF, 소유권 검증, 카테고리 관리, 보안 통합 테스트
4주: 월 예산/대시보드, 집계 쿼리, 모바일 UX, 거래 수정 충돌
5주: 정기결제·예정일 계산·일시정지, 통계, 단위·통합·E2E 테스트
6주: Docker/HTTPS 배포, 운영 로그, 오류·접근성 개선, README/ERD/API 문서/트러블슈팅

## 완료 기준
신규 가입자가 실제 배포 URL에서 거래를 기록하고 예산·구독 예정일을 조회할 수 있다. 다른 사용자의 데이터 접근은 차단된다. 핵심 E2E 시나리오와 서버 테스트가 통과한다. 시연 계정/가짜 데이터로 포트폴리오를 공개한다.


## 현재 개발 상태 (2026-10-20 기준, 사용자 확인 내용)
- Windows, IntelliJ IDEA 설치 및 프로젝트 열기 완료
- `java -version` 및 `javac -version`: 17.0.12 확인
- Spring Initializr로 Gradle-Groovy, Java, Spring Web, Validation 기반 `backend` 생성
- 로컬 저장소 경로 `C:\workspace\spendly`, 백엔드 `C:\workspace\spendly\backend`
- GitHub 저장소 생성 및 연결 완료(사용자 진술)
- 실제 Spring Boot 버전과 실행 성공 여부는 `backend/build.gradle` 및 실행 결과로 확인 필요
- 프론트엔드, PostgreSQL, 인증 및 거래 기능은 아직 구현 확인되지 않음

## Codex 협업 규칙
- 개발 시작 전 `AGENTS.md`, 관련 docs, 현재 코드와 Git 상태를 확인한다.
- 작은 기능 단위로 설계→구현→검증→설명하고, 사용자 승인 없이 커밋·푸시하지 않는다.
- 커밋 메시지는 접두어(`feat`, `fix`, `refactor`, `test`, `docs`, `chore` 등)만 영어로 작성하고 설명과 본문은 한국어로 작성한다. 예: `feat: 거래 등록 API 구현`.
- 기존 파일을 무단 덮어쓰지 않고, 민감정보를 커밋하지 않는다.
- 이 문서의 설계는 초기안이다. 모호한 결정은 구현 전에 질문하거나 선택지를 제시하고, 확정되면 docs에 기록한다.

## users 설계 확정 및 구현 상태 갱신 (2026-10-20)
사용자가 첫 Flyway 마이그레이션을 위해 아래 물리 설계를 승인했다. 이 절은 위 초기 상태 기록보다 최신이며, 다른 테이블 설계는 변경하지 않는다.
- id: UUID PRIMARY KEY, 애플리케이션 생성, DB 기본값 없음.
- email: VARCHAR(254) NOT NULL UNIQUE. 애플리케이션에서 앞뒤 공백 제거·소문자 통일 후 저장.
- password_hash: VARCHAR(255) NOT NULL.
- created_at: TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP.
- 사용자 제공 로그에서 PostgreSQL 16.14 연결, JPA·Flyway 초기화, 기존 테스트 통과 및 Java 17.0.12·Spring Boot 4.0.8 서버 기동을 확인했다(V1 추가 전).
- users V1 SQL의 실제 적용과 테이블 구조·이메일 UNIQUE 제약을 사용자 조회 결과로 확인했다. V1 추가 후 테스트 XML(1건·실패/오류 0)과 사용자 서버 재시작 로그(버전 1·추가 적용 없음·정상 기동)로 검증했으며 UUID 생성·이메일 정규화·인증 API 코드는 미구현이다.
