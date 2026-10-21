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

## 카테고리·거래 Repository 구현 (2026-10-20)
사용자 승인으로 기존 사용자 범위 조회 요구사항을 CategoryRepository·TransactionRepository에 구현했다. 도메인 요구사항과 스키마 변경은 없다.
- Spring Data Repository 인터페이스에서 save, findByIdAndUserId, findAllByUserId만 노출한다. 사용자 조건 없는 findById/findAll·삭제 메서드는 추가하지 않는다.
- 조회의 userId는 향후 인증 정보에서 얻어 서비스가 전달한다. Repository가 인증을 수행하지 않으며 save 자체도 소유권·카테고리·금액 규칙을 검증하지 않는다.
- 목록은 내부 사용자 범위 기본 조회다. API 정렬·페이지·필터 계약을 확정하거나 대체하지 않는다. 보관 카테고리도 포함하며 신규 선택 제한은 별도 서비스 책임이다.
- 두 사용자의 동일 이름 카테고리·거래로 저장/재조회 및 상세·목록 격리 통합 테스트 4개를 작성했다. 실행 결과는 ROADMAP을 따른다. 인증·서비스·API는 아직 없다.

## 인증 구현 순서 변경 (2026-10-20 사용자 승인)
원래 3주 차의 회원가입·로그인·세션·CSRF를 거래 서비스·HTTP API보다 먼저 구현한다. 이유는 사용자 범위 Repository에 실제 인증 사용자 ID를 연결하기 위해서다. 임시 고정 사용자나 클라이언트 user_id 기반 인증을 도입하지 않는다. MVP 범위는 유지한다. 인증 API·쿠키·CSRF·입력·오류·속도 제한의 구체적인 값은 AUTH_DESIGN 초안에서 제안하며 별도 사용자 확인 후 이 기준 문서에 확정 반영한다.

## 인증 정책 확정 및 구현 (2026-10-20 사용자 승인)
인증 우선 구현에 필요한 아래 정책을 승인받아 확정했다. AUTH_DESIGN 초안 확인 대기 상태를 갱신한다.
- GET /api/auth/csrf는 비인증 상태에서도 200으로 token·headerName을 반환한다. POST /api/auth/register는 JSON email·password로 가입하고 201 id·email 반환, 자동 로그인하지 않는다. POST /api/auth/login은 200 id·email 및 서버 세션 인증을 설정한다. POST /api/auth/logout은 204·세션 무효화·쿠키 제거, 올바른 CSRF가 있으면 비인증 상태에서도 멱등 처리한다. GET /api/me는 인증 사용자 id·email만 반환한다.
- 이메일은 strip·Locale.ROOT 소문자 정규화 후 필수·형식·최대 254자를 검증한다. 비밀번호는 필수·공백만 있는 값 금지·12~128자이며 공백/대소문자를 변환하지 않고 조합 규칙은 강제하지 않는다. PBKDF2-SHA256(Spring Security v5.8 기본 파라미터) 해시를 알고리즘 식별자와 저장한다.
- 회원가입·로그인·로그아웃 모두 CSRF를 검사한다. 토큰은 세션에 보관하고 JSON에서 받은 토큰을 X-CSRF-TOKEN 헤더로 보낸다. 로그인·로그아웃 뒤 재조회한다. 로그인 시 세션 ID를 교체하고 SecurityContext를 세션에 명시적으로 저장한다. 세션 principal에는 해시를 남기지 않는다.
- 세션 쿠키는 HttpOnly·SameSite=Lax, 비활성 30분 만료다. 기본 Secure=true이며 local 프로필의 로컬 HTTP에서만 false다. 쿠키 외 URL 세션 추적은 사용하지 않는다. 초기 프런트는 동일 출처 프록시를 사용하고 credential CORS 출처는 허용하지 않는다. 임의 전달 IP 헤더는 신뢰하지 않는다.
- 공통 오류는 code·message·details 배열이다. details는 field·message만 포함하고 원문 입력·비밀번호·해시·쿠키·토큰·SQL 오류를 로그/응답에 노출하지 않는다. 400 입력/JSON, 401 미인증·동일한 계정 없음/비밀번호 불일치, 403 CSRF/접근 거부, 409 이메일 중복/인증 상태 충돌, 429 로그인 제한을 사용한다. 표준 404·405·415·500도 같은 JSON 형식으로 처리한다. 인증된 상태의 가입·로그인은 409다.
- 로그인 제한은 직접 연결 IP별 5분 20회, IP+정규화 이메일별 5분 5회다. 입력 검증을 통과한 인증 시도를 성공/실패와 무관하게 집계하며 초과 시 429와 Retry-After를 반환한다. 단일 서버 메모리 고정 시간 창·만료 제거·최대 10000개 키, 저장소 포화 시 제한 응답을 사용한다. 재시작 시 초기화되며 다중 서버는 지원하지 않는다.
- UserRepository·인증 DTO/서비스/Controller·보안 설정·공통 JSON 오류·속도 제한을 구현했다. 거래 API·거래 입력 검증·버전 충돌의 HTTP 409 변환과 인증 ID 연결은 아직 미구현이다. 상세 실행·배포 검증은 AUTH_GUIDE와 ROADMAP을 따른다.

## 카테고리 서비스·API 계약 확정 (2026-10-20 사용자 승인)
기존 카테고리 요구사항의 응답·조회·수정·오류 정책을 아래처럼 구체화한다. 이전 카테고리 type/PATCH/응답 계약 미확정 상태를 갱신하며 다른 리소스의 계약은 변경하지 않는다.
- GET /api/categories: 200 DTO 배열, 없으면 []. includeArchived 기본 false, true면 보관 포함. 인증 사용자 범위에서 name 오름차순·id 오름차순으로 조회한다. 이번에는 페이지·검색을 도입하지 않는다.
- POST /api/categories: name·type 필수, 생성 201 DTO. name은 문자열 strip 후 빈 이름 금지·최대 50자·대소문자 구분, type은 문자열 INCOME·EXPENSE만 허용한다. archived=false로 시작한다.
- PATCH /api/categories/{id}: name만 필수로 받아 이름을 수정하고 200 DTO. 필드 누락·null·빈 객체·문자열 외 이름·type/archived/userId/user_id 등 지원하지 않는 필드는 400이다. 생성 요청도 name·type 외 필드는 거부한다. type은 생성 이후 변경할 수 없다.
- DELETE /api/categories/{id}: 물리 삭제 없이 archived=true, 반복 보관도 204다. 보관 카테고리 이름 수정은 허용하되 복원 API는 이번 범위가 아니다. 기존 거래 연결을 유지한다.
- DTO는 id·name·type·archived·createdAt만 포함한다. user_id는 세션 principal에서 얻으며 요청·응답 DTO에 포함하지 않는다.
- 동일 사용자·유형·정규화 이름 중복은 보관 상태와 무관하게 409 CATEGORY_ALREADY_EXISTS. 사전 검사 및 flush 시 DB UNIQUE(uk_categories_user_type_name) 오류를 처리한다. 다른 사용자·유형의 동일 이름은 허용한다.
- 타인 소유와 미존재 항목의 수정/보관은 동일한 404 CATEGORY_NOT_FOUND. 입력·UUID·쿼리 변환 오류는 400, 미인증은 401, CSRF 오류는 403이다. 보안 필터의 CSRF 우선 검사를 유지한다.
- 서비스를 트랜잭션으로 묶고 Repository 쿼리에 사용자 조건을 포함한다. Category는 rename·archive 변경 메서드와 DynamicUpdate를 사용해 이름/보관 수정 시 변경된 컬럼만 갱신한다. 스키마·카테고리 version 컬럼은 추가하지 않는다. 거래 API·보관 카테고리 신규 선택 검사·거래 버전 409 변환은 이후 구현한다.

## 카테고리 이름순 정렬 변경 (2026-10-20 사용자 요청)
카테고리를 이름으로 찾기 쉽도록 생성 시각순을 name ASC·id ASC로 변경한다. 같은 이름의 수입·지출 카테고리는 UUID 오름차순으로 순서를 고정한다. 기본 목록과 보관 포함 목록 모두 동일한 기준이며 사용자 범위·보관 필터는 유지한다. 스키마·DB collation은 변경하지 않는다. 현재 로컬 DB en_US.utf8의 예시 조회에서 한글 가나다순을 확인했으며, 다른 collation의 배포 환경에서는 다시 검증한다.

## 거래 등록·상세 조회 계약 및 구현 (2026-10-21)
feat/transaction의 구현 요청을 기준으로 등록·상세 계약을 구체화하고, 사용자 지시에 따라 메모 누락과 명시적 null을 모두 빈 문자열로 정규화한다. 기존 거래 메모 null 미확정 기록을 갱신한다.
- POST /api/transactions는 201, GET /api/transactions/{id}는 200. 응답은 기존 예시대로 id·type·amount·categoryId·occurredOn·description·memo·version이며 소유자·엔티티를 노출하지 않는다.
- 생성 필수값은 type·amount·categoryId·occurredOn·description. description은 strip 후 빈 내용 금지·최대 100자다. memo는 누락/null이면 '', 문자열이면 공백을 유지하며 최대 1000자다.
- type은 문자열 INCOME/EXPENSE, amount는 JSON 정수 숫자 표기이며 BigDecimal로 읽어 1~999999999999999를 검증한다. 문자열 숫자·소수 표기·지수 표기는 400이다. 내부 서비스 호출도 @Validated/@Valid와 금액 제약으로 DB 소수 반올림 전에 검증한다.
- occurredOn은 YYYY-MM-DD의 실제 날짜(연도 0001~9999), categoryId와 상세 경로 id는 표준 UUID 형식을 요구한다. 미래 날짜를 금지하는 제약은 추가하지 않는다. 알려지지 않은 필드·클라이언트 userId/user_id/id/version/시각 지정은 400이다.
- 없는/타인 거래는 동일 404 TRANSACTION_NOT_FOUND, 없는/타인 카테고리는 동일 404 CATEGORY_NOT_FOUND. 유형 불일치는 400 CATEGORY_TYPE_MISMATCH, 보관 카테고리 신규 선택은 400 CATEGORY_ARCHIVED다. 미인증 401·CSRF 403과 기존 필터 우선 순서를 유지한다.
- 등록 서비스는 인증 UUID로 카테고리를 조회하고 PESSIMISTIC_READ로 해당 행을 트랜잭션 종료까지 잠가 확인 직후 보관되는 경쟁을 보호한다. 기존 DB 복합 FK는 유지한다. 등록 후 보관된 카테고리의 기존 거래 상세는 조회할 수 있다.
- version은 0에서 시작한다. 등록·상세 Controller/DTO/Service와 필요한 Repository 메서드만 추가했으며 목록·수정·삭제·검색·페이지·수정 충돌 HTTP 409 변환은 별도 구현한다. 스키마·의존성·버전·기존 설정은 변경하지 않았다.


## 거래 목록·수정·삭제 확정 (2026-10-21)
사용자가 남은 거래 API 설계를 승인해 조회·부분 수정·삭제 계약을 확정했다. 앞선 미구현/미확정 기록에서 해당 항목을 갱신한다.
- GET /api/transactions: month(YYYY-MM, 0001~9999)·type(INCOME/EXPENSE)·categoryId(UUID)·q를 AND로 적용한다. month 누락은 전체 기간, 유효하지만 없는/타인 카테고리 필터는 빈 목록이다. q는 strip·최대 100자이며 description 또는 memo의 대소문자 무시 부분 검색이다. %, _, 역슬래시는 실제 문자로 검색한다.
- page 기본 0, size 기본 20(1~100). sort 기본 occurredOn,desc이며 occurredOn/amount/createdAt과 asc/desc만 허용한다. id도 같은 방향으로 정렬해 동률을 고정한다. 음수 페이지·미지원 조건과 JPA 조회 오프셋(page*size)이 정수 범위를 넘는 요청은 400이다.
- 목록 응답은 content·page·size·totalElements·totalPages. 조회 결과가 없으면 content=[]와 합계 0, 마지막 페이지를 넘으면 content=[]와 실제 전체 합계를 반환한다.
- PATCH /api/transactions/{id}: 비음수 정수 version 필수, type·amount·categoryId·occurredOn·description·memo 중 최소 1개를 전달한다. 누락은 유지, memo:null은 빈 문자열, 나머지 null과 미지원 필드는 400. 값 검증은 등록과 같다.
- 변경 후 사용자·유형이 일치해야 한다. 다른 카테고리를 선택하면 사용 중인 항목이어야 한다. 기존 카테고리 ID·유형을 유지하는 수정은 보관 후에도 허용한다.
- 현재와 다른 version 또는 실제 동시 수정/삭제 충돌은 409 TRANSACTION_VERSION_CONFLICT. 실제 수정은 version 증가, 동일 값 요청은 현재 version 유지다. 오래된 version은 동일 값이어도 409다.
- DELETE /api/transactions/{id}: 소유 거래 물리 삭제 204. version/본문 불필요. 타인·없는 거래·반복 삭제는 404 TRANSACTION_NOT_FOUND. 카테고리는 유지한다. 수정/삭제는 기존 세션·CSRF 정책을 따른다.
- 스키마·의존성·프레임워크 버전은 유지한다. 자세한 계약과 실행은 [설계](TRANSACTION_API_DESIGN.md)와 [가이드](TRANSACTION_API_GUIDE.md)를 따른다.


## 프런트엔드 초기 구성·첫 API 연동 확정 (2026-10-21)
사용자가 React·TypeScript·Vite·Tailwind에 shadcn/ui를 추가하고 로그인에서 거래 목록까지 연결하는 범위를 승인했다. UI 일관성과 첫 브라우저 통합 검증을 위한 기술 구성 보완이며 제품 기능 범위는 유지한다.
- frontend를 독립 npm 프로젝트로 구성한다. React·TypeScript·Vite·Tailwind·shadcn/ui(Radix/nova)·TanStack Query·React Hook Form·Zod를 사용한다. UI 소스는 프로젝트에서 관리하며 Button·Input·Label·Card를 먼저 도입한다.
- /login과 /transactions를 구현한다. 초기 진입·새로고침은 GET /api/me로 확인하고 미인증은 로그인으로 이동한다. 로그인·로그아웃 시 세션을 서버에서 관리하고 조회 캐시를 비운다. 소유 리소스 캐시 키에는 사용자 ID를 포함한다. 쿠키·CSRF·비밀번호를 localStorage/sessionStorage에 저장하지 않는다.
- 모든 API 요청은 동일 출처 상대 경로 /api와 세션 쿠키를 사용한다. 개발 시 Vite가 127.0.0.1:8080으로 프록시한다. 쓰기 요청마다 CSRF를 새로 조회하므로 로그인/로그아웃 전 토큰을 재사용하지 않는다. CORS 출처 허용·백엔드 보안 정책은 변경하지 않는다.
- 거래 목록은 전체 기간 기본값과 기존 서버의 월·유형·카테고리·검색·정렬·20건 페이지를 연결한다. 조건 변경은 첫 페이지로 이동하고 URL에 적용 조건을 유지한다. 카테고리 필터에는 보관 항목도 표시한다. 검색은 버튼/Enter로 적용한다.
- 한국어·모바일 우선 공통 레이아웃과 로딩·빈 목록·오류·재시도 상태를 제공한다. 금액 응답의 안전 정수/도메인 범위를 검증하고 정수 원화로 표시한다. 발생일은 DATE 문자열을 유지한다.
- 이번 범위에 회원가입 화면·거래 등록/수정/삭제 화면·예산/대시보드·구독 화면을 포함하지 않는다. 운영 리버스 프록시·SPA fallback·HTTPS는 배포 단계에서 별도 구성한다. 상세 실행·검증은 [FRONTEND_GUIDE](FRONTEND_GUIDE.md)를 따른다.


## 카테고리 관리·거래 등록 화면 (2026-10-21)
사용자가 feat/transaction-regist에서 카테고리 관리와 첫 거래 등록 화면 구현을 승인했다. 기존 확정 API 정책을 화면으로 연결하며 스키마·서버 계약은 변경하지 않는다.
- 인증 사용자용 /settings/categories에서 카테고리 생성·이름 변경·삭제를 제공한다. /settings는 해당 화면으로 이동한다. 이름 1~50자와 유형을 검증하고, 생성 후 유형은 수정하지 않는다. 삭제 확인에서 기존 거래 유지와 신규 선택 제외를 안내한다. 삭제 항목 포함 필터와 삭제 후 이름 변경을 제공한다. 복원은 범위 밖이다.
- /transactions/new에서 유형·금액·카테고리·발생일·설명·메모를 입력한다. 기본 유형은 지출, 발생일은 Asia/Seoul 오늘이다. 금액은 숫자로 된 정수 문자열을 검증한 뒤 1~999999999999999 및 Number 안전 정수 범위에서 JSON 숫자로 전달한다. 소수·지수·구분 쉼표·음수는 거부한다.
- 선택한 유형과 일치하는 사용 중인 카테고리만 표시한다. 유형 변경 시 카테고리 선택을 초기화한다. 선택 가능한 항목이 없으면 저장을 막고 관리 화면을 안내한다. 관리 화면은 새 탭으로 열어 작성 내용을 유지하고 카테고리 새로고침을 제공한다. 서버의 소유권·유형·삭제 상태 검증이 최종 기준이다.
- 발생일은 실제 YYYY-MM-DD(0001~9999), 설명은 strip 후 1~100자, 메모는 선택 최대 1000자·공백 유지이며 미입력은 빈 문자열이다. 실패 시 입력을 유지하고 중복 이름·카테고리 변경 오류를 안내한다. 연속 제출은 진행 중 요청 하나로 제한한다.
- 카테고리 변경 후 사용자 카테고리 캐시를, 거래 등록 후 사용자 거래 목록 캐시를 갱신한다. 등록 성공은 전체 기간 기본 목록으로 이동해 완료 메시지를 표시한다. 기존 세션·CSRF·401 정리 정책을 유지한다.
- 이번 작업은 프런트엔드 화면이며 회원가입·거래 수정/삭제 화면은 이후 범위다. 검증 결과와 차단 사항은 ROADMAP, 수동 절차는 FRONTEND_GUIDE에 기록한다.
