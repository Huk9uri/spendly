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

## categories 물리 설계 확정 (2026-10-20)
사용자 승인에 따라 V2 마이그레이션을 위한 물리 설계를 확정한다. 이름 길이·필수값·기본값·외래 키 정책을 구체화하기 위한 변경이다.
- id: UUID PRIMARY KEY, 애플리케이션 생성, DB 기본값 없음.
- user_id: UUID NOT NULL, users.id 참조, ON DELETE RESTRICT. 카테고리가 있는 사용자 삭제를 차단한다.
- name: VARCHAR(50) NOT NULL. 애플리케이션에서 앞뒤 공백 제거·빈 이름 검증 후 저장한다. DB에서도 btrim 기준 빈 이름을 CHECK로 차단한다. 영문 대소문자를 구분한다.
- type: VARCHAR(7) NOT NULL, CHECK로 INCOME·EXPENSE만 허용한다.
- archived: BOOLEAN NOT NULL DEFAULT FALSE.
- created_at: TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP.
- UNIQUE(user_id,type,name)은 보관 상태와 무관하게 적용한다. 보관한 이름도 같은 사용자·유형에서 중복 생성할 수 없다.
- DELETE API는 보관 처리한다. 보관 카테고리의 신규 거래 선택 가능 여부·복원 정책은 미확정으로 유지한다.
- V2 SQL 작성과 API 구현을 구분한다. 사용자 소유권 검증·이름 정규화·UUID 생성·카테고리 API는 아직 미구현이다.

## transactions 물리 설계 확정 (2026-10-20)
사용자가 V3 구현을 위해 승인한 설계다. 금액·필수값·문자열 길이·카테고리 무결성·인덱스를 구체화하며, 아래 보관 정책은 이전의 신규 거래 선택 미확정 기록을 갱신한다.

| 컬럼 | 정의 |
| --- | --- |
| id | UUID PRIMARY KEY, 애플리케이션 생성, DB 기본값 없음 |
| user_id | UUID NOT NULL, users.id FK, 삭제·키 변경 RESTRICT |
| category_id | UUID NOT NULL, 카테고리 소유자·유형과 함께 복합 FK |
| type | VARCHAR(7) NOT NULL, INCOME·EXPENSE CHECK |
| amount | NUMERIC(15,0) NOT NULL, 1~999999999999999 CHECK |
| occurred_on | DATE NOT NULL, 기본값 없음, 사용자 로컬 날짜(초기 Asia/Seoul) |
| description | VARCHAR(100) NOT NULL, btrim 기준 빈 설명 CHECK |
| memo | VARCHAR(1000) NOT NULL DEFAULT '', 선택 입력은 빈 문자열로 저장 |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP |
| version | BIGINT NOT NULL DEFAULT 0, 음수 금지 CHECK |

- V3에서 categories에 UNIQUE(id,user_id,type)을 추가하고 transactions(category_id,user_id,type)이 참조한다. 거래가 참조하는 카테고리의 물리 삭제·소유자·유형 변경은 RESTRICT로 차단한다. 이름 변경·보관 처리는 허용한다. V1/V2는 수정하지 않는다.
- 인덱스: (user_id,occurred_on DESC,id DESC), (category_id,user_id,type).
- 금액은 서버 BigDecimal로 검증·계산한다. NUMERIC(15,0)은 소수 입력을 반올림할 수 있으므로 API에서 저장 전에 소수·범위 밖 금액을 거부한다. DB 범위 CHECK는 NaN도 거부한다. 클라이언트는 Number 안전 정수와 컬럼 범위를 모두 확인한다.
- updated_at 갱신과 version 증가는 향후 JPA에서 처리하며 DB 자동 갱신 트리거는 두지 않는다. V3만으로 낙관적 잠금·409 응답이 구현되지는 않는다.
- 보관 카테고리는 신규 거래 생성·카테고리 변경 시 선택을 금지한다. 이미 연결된 보관 카테고리를 유지하는 금액·메모 수정은 허용한다. 이 정책은 향후 애플리케이션에서 구현한다. 카테고리 복원 정책은 미확정이다.
- 소유권 검증·소수 입력 검증·보관 정책·엔티티·API·충돌 처리는 미구현이며, 이번 범위는 V3 SQL과 DB 제약조건 검증 SQL이다.

## JPA 엔티티 매핑 (2026-10-20)
사용자 승인으로 V1~V3 물리 설계를 Java 엔티티에 연결한다. 요구사항과 스키마는 유지하며 구현 방식을 다음과 같이 기록한다.
- User, Category, Transaction을 각각 users, categories, transactions에 매핑한다. UUID는 JPA GenerationType.UUID로 애플리케이션 프로세스에서 생성한다.
- 소유자·카테고리 참조는 UUID 필드로 매핑하며 JPA 객체 연관관계·cascade는 도입하지 않는다. DB의 기존 FK·복합 FK는 그대로 적용된다.
- TransactionType enum은 INCOME·EXPENSE를 문자열로 저장한다. 금액은 BigDecimal(precision=15, scale=0), 발생일은 LocalDate, 생성·수정 시각은 Instant다.
- JPA PrePersist로 생성 시각을, 거래 PreUpdate로 수정 시각을 설정한다. DB 시각 기본값은 SQL 직접 입력용으로 유지한다. 거래 version에 @Version을 매핑해 ORM 수정 충돌을 감지한다.
- 이번 엔티티는 저장 매핑 범위다. 생성자에 전달되는 이메일·카테고리명·금액·메모 등의 API 정규화와 입력 검증, 비밀번호 해시 생성, 인증 소유권·보관 정책 검사는 향후 서비스에서 구현해야 한다. 엔티티를 API 응답으로 직접 노출하지 않는다.
- API 409 변환은 아직 미구현이며 @Version 테스트와 API 충돌 응답 검증은 구분한다. 실행 검증 상태는 ROADMAP을 따른다.
