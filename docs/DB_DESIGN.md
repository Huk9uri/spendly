# Spendly DB 설계

기준일: 2026-10-20. 기준: [PROJECT_SPEC](PROJECT_SPEC.md)의 DB 논리 모델과 도메인 규칙. users V1 적용을 검증했으며 categories V2 적용과 컨텍스트 테스트·서버 재시작을 확인했다(사용자 psql 결과로 제약조건 SQL 통과·ROLLBACK 확인). 엔티티와 나머지 테이블은 미구현이다.

## 논리 모델
| 테이블 | 필드와 제약 |
| --- | --- |
| users | id UUID PK, email UNIQUE, password_hash, created_at |
| categories | id UUID PK, user_id FK, name, type INCOME 또는 EXPENSE, archived, created_at; UNIQUE(user_id,type,name) |
| transactions | id UUID PK, user_id FK, category_id FK, type, amount NUMERIC(15,0), occurred_on DATE, description, memo, created_at, updated_at, version; CHECK(amount>0) |
| budgets | id UUID PK, user_id FK, month DATE, amount NUMERIC(15,0); UNIQUE(user_id,month), CHECK(amount>=0); month는 매월 1일 |
| subscriptions | id UUID PK, user_id FK, category_id FK, name, amount NUMERIC(15,0), billing_day INT 1..31, start_date DATE, status ACTIVE 또는 PAUSED, created_at, updated_at |

users는 categories·transactions·budgets·subscriptions 각각과 1:N이다. categories는 transactions·subscriptions 각각과 1:N이다.

## 무결성과 도메인 규칙
- 금액은 정수 원화다. 거래 금액은 양수이며 type으로 수입·지출을 구분한다. 서버는 BigDecimal 또는 Long으로 정확하게 계산하고 클라이언트는 Number 안전 정수 범위를 검증한다.
- 거래의 category는 동일 user 소유이고 거래 type과 일치해야 한다. 사용자 식별자는 인증 세션에서 결정한다.
- 거래가 있는 카테고리는 삭제 대신 archived로 처리한다. DELETE API는 archive 동작이다.
- 예산은 사용자·월당 하나이고 month는 해당 월 1일이다. 소비 합계는 지출만 포함하고 환불은 수입으로 기록한다.
- 구독은 예정 정보만 보관하며 거래를 자동 생성하지 않는다. PAUSED는 예정 결제에서 제외한다. 31일 등 존재하지 않는 결제일은 해당 월 마지막 날로 보정한다.
- 거래 수정에는 version 기반 낙관적 잠금을 적용하고 충돌은 409로 반환한다.
- occurred_on은 DATE이며 사용자 로컬 날짜, 초기 Asia/Seoul 기준이다.

## 물리 설계에서 남은 결정
PostgreSQL·JPA·Flyway는 목표 기술이다. UUID 생성 방식, 문자열 길이, 각 필드 null 허용 여부, 시각 컬럼 타입, 인덱스, FK 삭제 정책, 보관 카테고리의 재선택·이름 재사용, 구독 카테고리의 유형·보관 정책, 구독 금액 제약 및 start_date와 월말 보정의 경계 처리는 원본에 상세히 정해져 있지 않다. 구현 전 확인하고 기준 문서와 함께 갱신한다. 추가 제약을 확정된 요구사항처럼 도입하지 않는다.

연결·마이그레이션별 검증 상태와 예정된 백업 복구 검증은 [로드맵](ROADMAP.md)에서 관리한다.

## categories 물리 설계 — 2026-10-20 사용자 승인
첫 카테고리 마이그레이션을 위해 PROJECT_SPEC과 함께 구체화했다.

| 컬럼 | 정의 |
| --- | --- |
| id | UUID PRIMARY KEY, 애플리케이션 생성 |
| user_id | UUID NOT NULL, users.id 참조, ON DELETE RESTRICT |
| name | VARCHAR(50) NOT NULL, btrim 기준 빈 이름 차단 |
| type | VARCHAR(7) NOT NULL, INCOME·EXPENSE CHECK |
| archived | BOOLEAN NOT NULL DEFAULT FALSE |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP |

SQL: [V2__create_categories.sql](../backend/src/main/resources/db/migration/V2__create_categories.sql).
UNIQUE(user_id,type,name)은 보관 여부와 관계없이 적용하며 이름은 대소문자를 구분한다. 애플리케이션에서 앞뒤 공백 제거와 빈 이름 검증을 수행할 예정이며, DB CHECK는 정규화를 대신하지 않는다. FK는 사용자 존재만 검사하므로 API에서 인증 사용자 기준 소유권 검증을 별도로 구현해야 한다. 보관 카테고리의 신규 거래 선택·복원 정책은 미확정이다.

## users 물리 설계 갱신 (2026-10-20)
첫 마이그레이션 구현을 위한 사용자 승인으로 PROJECT_SPEC과 함께 확정했다. 이 절은 위의 초기 미구현 상태와 미확정 항목 중 users에 해당하는 부분을 갱신한다.

| 컬럼 | 정의 |
| --- | --- |
| id | UUID PRIMARY KEY, 애플리케이션 생성, DB 기본값 없음 |
| email | VARCHAR(254) NOT NULL, UNIQUE 제약 uk_users_email |
| password_hash | VARCHAR(255) NOT NULL |
| created_at | TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP |

SQL: [V1__create_users.sql](../backend/src/main/resources/db/migration/V1__create_users.sql). 이메일 공백 제거·소문자 통일은 향후 애플리케이션에서 수행한다. DB UNIQUE 자체는 이메일 대소문자를 정규화하지 않는다. 연결 성공과 V1 적용 성공(version=1, description=create users, success=t)은 사용자 로그로 확인했다. 실제 users 구조도 승인 설계와 일치한다. V1 추가 후 기존 컨텍스트 테스트 통과는 로컬 XML(2026-10-20 17:38, 1건·실패/오류 0)로, 중복 적용 없는 정상 서버 재시작은 사용자 제공 17:39 로그로 확인했다. 엔티티와 나머지 테이블은 없다. 다른 테이블의 물리 설계와 백업 복구 검증은 여전히 미확정·예정이다.

## transactions V3 설계 (2026-10-20 사용자 승인)
거래 테이블 구현을 위해 PROJECT_SPEC과 함께 확정했다. 필드 정의는 PROJECT_SPEC의 transactions 물리 설계 표를 기준으로 한다.
- [V3__create_transactions.sql](../backend/src/main/resources/db/migration/V3__create_transactions.sql): 필수 컬럼, 금액·유형·빈 설명·버전 CHECK, 사용자 FK, 카테고리 복합 FK, 조회 인덱스 2개를 정의한다.
- categories에 UNIQUE(id,user_id,type)을 추가하고 transactions(category_id,user_id,type)이 참조한다. NULL을 허용하지 않아 복합 FK 검사를 우회하지 못한다. 참조 중인 카테고리의 삭제·소유자·유형 변경은 RESTRICT이며, 이름 변경·보관은 허용한다. V1/V2는 보존한다.
- 금액은 NUMERIC(15,0), 1~999999999999999 CHECK로 NaN도 차단한다. 소수는 DB 형변환 중 반올림될 수 있으므로 입력 단계에서 BigDecimal로 정수·범위를 검증한다.
- description은 필수 100자, btrim 기준 빈 설명 금지다. memo는 최대 1000자, 미입력은 빈 문자열이다. 날짜 기본값은 없으며 사용자가 전달한다.
- version은 BIGINT 기본 0·음수 금지다. updated_at은 현재 시각 기본값만 제공한다. 실제 갱신·버전 증가와 충돌 감지는 향후 JPA에서 수행한다.
- 보관 카테고리 신규 선택 금지·기존 연결 유지 수정 허용은 애플리케이션 정책으로 확정했으며, DB FK가 이 정책이나 인증 소유권을 대신하지 않는다. 복원 정책은 미확정이다.
- [verify_transactions.sql](../backend/src/test/resources/db/verify_transactions.sql)은 기본값·경계값·잘못된 금액·필수값·문자열 길이·유형·버전·소유자/유형 불일치·참조 삭제/변경 차단·보관 후 기존 연결 유지를 검사하고 테스트 데이터를 롤백한다. psql로 별도 실행하며 Gradle이 자동 실행하지 않는다. 실행 결과는 ROADMAP에서 관리한다.

## transactions V3 검증 결과 (2026-10-20)
로컬 19:47 컨텍스트 테스트 XML(1건·실패/오류 0·V3 최초 적용), 사용자 Flyway 이력(version=3, success=t)과 verify_transactions.sql 성공 NOTICE·ROLLBACK으로 DB 적용·제약조건 검증을 확인했다. 사용자 19:48 서버 로그에서 현재 버전 3·추가 적용 없는 정상 기동을 확인했다. 애플리케이션의 입력·소유권·보관 정책과 낙관적 잠금은 여전히 미구현이다.

## JPA 엔티티 매핑 (2026-10-20)
users→User, categories→Category, transactions→Transaction을 매핑했다. 생성 시 UUID는 JPA가 애플리케이션에서 생성한다. 사용자·카테고리 참조는 UUID 필드이며 무결성은 V1~V3 FK를 유지한다. TransactionType은 STRING, 금액은 BigDecimal(15,0), DATE는 LocalDate, TIMESTAMPTZ는 Instant다. 생성 시각은 PrePersist, 거래 수정 시각은 PreUpdate, 거래 version은 @Version으로 관리한다.
엔티티·테스트 소스 컴파일은 통과했고 사용자 20:05 실행과 로컬 XML로 총 4건·실패/오류/건너뜀 0, 사용자 20:10 서버 로그로 ddl-auto=validate와 정상 기동을 확인했다. 저장·수정·버전 충돌 테스트가 통과했다. DB 스키마는 수정하지 않았다. 입력 소수 금액 검증·소유권·보관 카테고리 정책은 엔티티 생성자에서 구현하지 않았으며 향후 서비스 책임이다.

## Repository 구현 범위 (2026-10-20)
CategoryRepository·TransactionRepository에서 사용자 ID와 리소스 ID로 상세 조회하고, 사용자 ID로 목록 조회하도록 구현했다. 기존 DB 스키마·제약조건·엔티티는 변경하지 않았다. RepositoryTests의 DB 저장·재조회·사용자 격리 4개 테스트는 사용자 실행과 로컬 XML로 통과를 확인했다(전체 8건·실패/오류/건너뜀 0). 사용자 21:01 서버 로그에서 Repository 2개·정상 기동도 확인했으며 근거는 ROADMAP에 기록했다. 목록은 정렬·페이지 계약이 없는 내부 기본 조회이고 보관 카테고리를 포함한다.

## 인증 저장·검증 구현 (2026-10-20)
UserRepository의 이메일 조회·중복 확인·saveAndFlush를 인증 서비스에 연결했다. 기존 users UNIQUE(uk_users_email)와 flush 시 제약조건명을 확인해 동시 중복 가입도 409로 변환한다. password_hash에는 {pbkdf2@SpringSecurity_v5_8} 식별자와 PBKDF2 해시를 저장한다. 컬럼 255자와 기존 V1~V3는 유지한다. 스키마 변경·마이그레이션은 없다.
HttpSession에는 UUID·이메일 principal을 보관하고 인증 성공 시 자격정보를 지운다. CSRF 토큰·세션·로그인 제한은 DB 테이블에 저장하지 않는다. 실제 PostgreSQL 회원가입·해시 저장·중복 확인 테스트 2개는 사용자 실행의 21:58 XML에서 통과를 확인했다. 전체 20개 테스트도 실패/오류/건너뜀 0이며 사용자 실제 HTTP 가입·로그인·me·로그아웃 후 401 흐름도 확인했다. 검증 근거는 ROADMAP을 따른다.

## 카테고리 서비스·Repository 보완 (2026-10-20)
기존 스키마·V1~V3는 유지했다. Category에 rename·archive와 DynamicUpdate를 추가하고 Repository에 사용자 범위 보관 필터·name/id 정렬·이름 중복 검사·saveAndFlush를 추가했다. 보관한 이름도 기존 UNIQUE 범위에 포함되며 이름만 수정한다. flush 오류의 uk_categories_user_type_name을 확인해 동시 중복도 409로 변환한다. 카테고리 서비스·API를 구현했고 Codex의 별도 임시 PostgreSQL 전체 32개 테스트(실패/오류/건너뜀 0)로 DB 검증을 마쳤다. 임시 서버의 실제 HTTP 흐름도 통과했고 임시 리소스를 정리했다. 상세 근거는 ROADMAP을 따른다.

## 이름순 정렬 갱신 (2026-10-20)
사용자 요청으로 기존 name ASC·id ASC 문구를 확정하고 기본/보관 포함 Repository 조회를 변경했다. DB collation이나 인덱스·V1~V3는 수정하지 않았다. 현재 로컬 en_US.utf8에서 한글 가나다순 예시를 확인했고 임시 DB에서 관련 16개 테스트를 통과했다. 배포 환경 collation에 따라 혼합 문자 순서가 달라질 수 있어 재확인한다.

## 거래 등록·상세 저장 검증 (2026-10-21)
V1~V3 스키마는 변경하지 않았다. TransactionRepository.saveAndFlush로 검증된 정수 금액·DATE·메모를 저장하고, 상세는 인증 userId와 id로 제한한다. 사용자 지시에 따라 memo 누락/null은 모두 빈 문자열로 정규화한다. 카테고리 등록용 조회에 PESSIMISTIC_READ를 적용해 동일 행의 보관 갱신과 등록 검사의 경쟁을 보호한다. 기존 거래는 보관 후에도 조회한다.
실제 PostgreSQL에서 최소/최대 금액·윤년 날짜·version0·메모 저장, 소수 입력 선행 검증·소유권/유형/보관 상태, 별도 연결의 행 잠금 검증을 통과했다. 기존 회귀 포함 48개 모두 통과했으며 임시 테스트 DB·서버와 커밋된 잠금 테스트 fixture도 정리했다. 수정·삭제·목록 API는 미구현이다.


## 거래 목록·수정·삭제 반영 (2026-10-21)
사용자가 설계를 승인해 구현한다. 추가 마이그레이션 없이 transactions.version을 수정·삭제 충돌 검출에 사용한다. 실제 수정 시 버전 증가, 동일 값 수정은 버전 유지, 거래 삭제는 물리 삭제다. 목록은 인증 사용자 조건을 필수로 적용하고 DATE 월 경계·정렬 동률의 id를 사용한다. 계약 기준은 [PROJECT_SPEC](PROJECT_SPEC.md)의 최신 거래 절이다.
