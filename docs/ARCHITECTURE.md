# Spendly 아키텍처

기준일: 2026-10-20. 요구사항 기준: [PROJECT_SPEC](PROJECT_SPEC.md).

## 실제 저장소 설정
| 항목 | 확인된 값과 근거 |
| --- | --- |
| Java | Toolchain 17, backend/build.gradle |
| Spring Boot | 4.0.8, backend/build.gradle |
| 의존성 관리 | io.spring.dependency-management 1.1.7 |
| Gradle Wrapper | 9.8.1, backend/gradle/wrapper/gradle-wrapper.properties |
| 프로젝트 | group com.spendly, version 0.0.1-SNAPSHOT, Maven Central |
| 프로젝트·앱 이름 | backend, settings.gradle 및 application.properties |
| 구현 의존성 | spring-boot-starter-webmvc, spring-boot-starter-validation |
| 테스트 의존성 | validation-test, webmvc-test, junit-platform-launcher; useJUnitPlatform() |

`backend/src/main/java/com/spendly/backend/BackendApplication.java`에 애플리케이션 진입점만 있다. `BackendApplicationTests.contextLoads()`는 @SpringBootTest 컨텍스트 로딩 테스트 소스이며 통과 여부는 이번 작업에서 검증하지 않았다. application.properties에는 애플리케이션 이름만 있다. frontend 디렉터리는 비어 있고 Git 추적 파일도 없다.

## 목표 구성 — 미구현
- 프런트엔드: React + TypeScript + Vite, TanStack Query, React Hook Form, Zod, Tailwind.
- 백엔드: Java 17 + Spring Boot + Spring Security + JPA.
- 저장·운영: PostgreSQL, Flyway, Docker.
- 검증: JUnit, Vitest, Playwright.
- 보안: 세션 쿠키 HttpOnly, 운영 Secure, SameSite 정책, CSRF 방어 활성화, 비밀번호 해시, 로그인 속도 제한, CORS 출처 제한. 개인정보·인증값 로그 제외.
- 원본은 프런트·API의 동일 사이트/리버스 프록시 제공을 쿠키 설정 단순화 방안으로 제시한다. 구체적 배포 구성과 정책 값은 미확정이다.

## 요구사항과 현재 코드의 차이
| 요구사항 | 현재 코드 | 처리 |
| --- | --- | --- |
| React 및 프런트엔드 도구·테스트 | 프로젝트 파일 없음 | 예정으로 기록, 설치하지 않음 |
| Security·세션·CSRF·소유권 검증 | 의존성·구현 없음 | 예정으로 기록, 인증 설정 변경 없음 |
| JPA·PostgreSQL·Flyway 및 5개 모델 | 의존성·연결·엔티티·마이그레이션 없음 | 설계만 문서화 |
| 업무 REST API·거래·예산·구독·집계 | Controller·Service·Repository 없음 | 전체 미구현으로 명시 |
| Docker·HTTPS·배포 | 관련 구성 파일 없음 | 예정으로 기록 |
| 핵심 서버·E2E 테스트 | 컨텍스트 로딩 테스트 소스만 존재 | 테스트 통과나 서비스 완성을 주장하지 않음 |
| Java 17 | Toolchain 17 | 설정 일치, 설치 버전은 이번 작업에서 재확인하지 않음 |
| Spring Boot 실행 확인 | 선언 버전 4.0.8 | 버전 확인 완료, 실행 성공은 미검증 |

원본의 Java 17.0.12 설치·IDE 준비·GitHub 연결은 사용자 확인 내용이다. 이번 작업에서 원격 연결이나 실행 성공을 별도로 검증하지 않았다. 위 미구현 차이를 없애기 위한 코드·설정 수정은 수행하지 않는다.

## 이전 파생 문서와 기준 문서의 차이
기존 문서는 요구사항 전달 전 가정에 기반했다. 이번 정리에서 `/api/v1`을 `/api`로, 거래 PUT을 PATCH로, 문자열 금액을 숫자 금액으로, transactionDate를 occurredOn으로 정정했다. UUID, NUMERIC(15,0), PostgreSQL·JPA·Flyway, 세션 인증, 예산·구독을 반영했다. 카테고리 삭제 제한 제안은 원본의 archive 동작으로 교체했다. 이는 구현 변경이 아닌 문서 정합성 수정이며 PROJECT_SPEC 원문은 보존한다.

## 개발 명령
저장소 루트에서 실행한다. Java 17과 Wrapper 배포본·의존성을 준비해야 한다.

| 목적 | Windows PowerShell | POSIX |
| --- | --- | --- |
| 실행 | `.\backend\gradlew.bat -p backend bootRun` | `./backend/gradlew -p backend bootRun` |
| 테스트 | `.\backend\gradlew.bat -p backend test` | `./backend/gradlew -p backend test` |
| 빌드 | `.\backend\gradlew.bat -p backend build` | `./backend/gradlew -p backend build` |

이번 작업은 문서 대조만 수행했으며 위 명령 실행 및 버전 조합 검증은 하지 않았다. 프런트엔드 실행 명령은 실제 프로젝트 생성 후 기록한다.

## 현재 구현 상태 갱신 (2026-10-20)
이 절은 위 초기 조사 시점의 설정 표와 미구현 차이 표를 갱신한다.
- build.gradle에 data-jpa·flyway starter와 PostgreSQL 드라이버·Flyway PostgreSQL 모듈이 추가되어 있다. Java 17·Boot 4.0.8·Wrapper 9.8.1은 유지한다.
- application.properties는 localhost:5432/spendly, 사용자 spendly, 환경변수 SPENDLY_DB_PASSWORD로 연결한다. Hibernate ddl-auto=validate, open-in-view=false, Flyway 활성화 상태다.
- compose.yaml은 PostgreSQL 16, 127.0.0.1:5432, 영속 볼륨·헬스체크를 구성한다. HTTPS·배포는 없다.
- 사용자 제공 로그로 PostgreSQL 16.14 연결, JPA·Flyway 초기화, 컨텍스트 테스트 통과와 8080 서버 기동을 확인했다(V1 추가 전).
- 승인된 users 물리 설계로 V1__create_users.sql을 작성했다. 사용자 조회 결과로 V1 적용 성공과 실제 테이블 구조를 확인했다. V1 추가 후 기존 컨텍스트 테스트 통과는 로컬 XML(2026-10-20 17:38, 1건·실패/오류 0)로, 중복 적용 없는 정상 서버 재시작은 사용자 제공 17:39 로그로 확인했다. 엔티티·Repository·인증·거래 API·프런트엔드는 없다.
- 테스트와 실행에는 동작 중인 DB와 SPENDLY_DB_PASSWORD 환경변수가 필요하다. 실행 결과와 차단 사항은 ROADMAP에서 관리한다.

## categories V2 갱신 (2026-10-20)
사용자 승인으로 PROJECT_SPEC·DB 설계의 카테고리 물리 설계를 구체화하고 `V2__create_categories.sql`을 작성했다. 사용자 FK(RESTRICT), 이름·유형·보관 여부·시각의 필수값과 기본값, 유형·빈 이름 CHECK, 사용자·유형·이름 UNIQUE를 정의한다. V1은 수정하지 않았다. 로컬 19:12 테스트 XML(1건·실패/오류 0)로 V2 적용과 컨텍스트 테스트 통과를, 사용자 19:13 서버 로그로 버전 2·추가 적용 없는 정상 재시작을 확인했다. 사용자 제공 psql 결과에서 제약조건 검증 성공 NOTICE와 ROLLBACK을 확인했다. 애플리케이션의 이름 정규화·소유권 검증·UUID 생성·엔티티·API는 아직 없다.

## transactions V3 갱신 (2026-10-20)
승인된 PROJECT_SPEC 설계에 따라 V3 SQL과 verify_transactions.sql을 작성했다. V3는 categories 복합 UNIQUE 추가와 transactions 테이블·복합 FK·인덱스를 정의하며 V1/V2는 유지한다. 다른 사용자의 카테고리·유형 불일치·참조 카테고리 삭제/키 변경을 DB에서 차단한다. 금액 정수 입력 검증, 보관 카테고리 신규 선택 제한, updated_at·version 갱신, 낙관적 잠금·409와 인증 소유권 검증은 향후 API·JPA 책임이며 미구현이다.
Codex 환경에 SPENDLY_DB_PASSWORD가 없고 Docker API 접근이 permission denied로 차단되어 DB 실행 검증은 수행하지 못했다. 이후 로컬 19:47 테스트 XML(1건·실패/오류 0·V3 적용), 사용자 이력 조회와 검증 SQL 성공 NOTICE·ROLLBACK, 19:48 서버 로그(버전 3·추가 적용 없음·8080 기동)로 V3 검증 완료를 확인했다.

## JPA 매핑 구현 (2026-10-20)
User·Category·Transaction과 공통 TransactionType enum을 추가했다. UUID는 GenerationType.UUID로 생성하고, userId·categoryId는 UUID 값으로 매핑한다. 객체 연관관계와 cascade 없이 기존 DB FK를 유지한다. 금액은 BigDecimal precision=15/scale=0, 발생일은 LocalDate, 시각은 Instant다. PrePersist/PreUpdate로 시각을 설정하고 거래 version은 @Version으로 관리한다. V1~V3·의존성·애플리케이션 설정은 변경하지 않았다.
EntityMappingTests는 실제 PostgreSQL을 사용하는 @SpringBootTest·@Transactional 테스트 3개다. 최대 금액·날짜·enum·UUID·시각의 저장/재조회, 수정 시각·버전 증가, stale version merge 거부를 검사한다. 각 테스트 fixture는 트랜잭션 롤백으로 제거한다. Flyway 스키마 이력 자체는 테스트 fixture와 별도로 유지된다. 업무 수정 메서드는 추가하지 않았으며 테스트에서만 ReflectionTestUtils로 필드를 바꿔 dirty checking을 유발한다.
Gradle compileJava/compileTestJava는 캐시 접근 권한을 받아 통과했다. Codex 프로세스에 DB 비밀번호 환경변수가 없어 직접 DB 테스트를 실행하지 않았다. 이후 사용자 20:05 실행 결과와 로컬 XML로 총 4건(기존 1+신규 3)·실패/오류/건너뜀 0, 사용자 20:10 로그로 ddl-auto=validate 상태의 정상 기동을 확인했다. 입력 검증·소유권·보관 정책·Repository·API·409 변환은 미구현이다.

## 카테고리·거래 Repository 구현 (2026-10-20)
CategoryRepository와 TransactionRepository를 추가했다. JpaRepository의 모든 기본 메서드를 상속하는 대신 Repository 마커 인터페이스에 save 및 사용자 범위 상세·목록 조회만 선언했다. Spring Data가 구현체를 생성하며 조회는 findByIdAndUserId·findAllByUserId로 사용자 조건을 포함한다. save는 기존 CrudRepository 시그니처와 일치한다.
RepositoryTests 4개는 실제 PostgreSQL과 @SpringBootTest·@Transactional을 사용한다. 사용자 2명의 fixture를 저장하고 flush/clear 후 본인 조회 성공·타인 ID 조회 결과 없음·서로 분리된 목록·미존재 ID/사용자 결과 없음을 검사한다. 테스트 fixture는 롤백한다. Gradle 소스·테스트 컴파일은 통과했다. 이후 사용자 BUILD SUCCESSFUL과 로컬 20:58~20:59 XML로 총 8건(기존 4+RepositoryTests 4)·실패/오류/건너뜀 0, 사용자 21:01 로그로 Repository 2개·버전 3·8080 정상 기동을 확인했다. DB 테스트는 사용자 실행 결과이며 Codex 직접 실행은 아니다.
내부 목록 조회는 정렬·페이지·검색·보관 여부를 적용하지 않는다. HTTP 계약은 기존 미확정 상태다. 인증 사용자 ID 전달·저장 시 소유권/카테고리 검증은 서비스에서 구현해야 하며 조회 조건 테스트가 전체 인증·보안 완료를 의미하지 않는다. 스키마·의존성·설정은 유지했고 기존 staged Transaction.java 주석 변경을 보존했다.

## 인증 설계 착수 (2026-10-20)
사용자 승인으로 세션·CSRF 인증을 거래 서비스·HTTP API보다 앞당긴다. 임시 사용자 없이 Repository에 실제 인증 사용자 ID를 연결하기 위한 순서 변경이며 PROJECT_SPEC에 기록했다. [AUTH_DESIGN](AUTH_DESIGN.md)은 세부 입력·응답·쿠키·CSRF·속도 제한의 검토용 초안이다. 세부 계약 확정 전이므로 보안 의존성·코드는 추가하지 않았다.

## 세션 인증 구현 (2026-10-20 사용자 승인)
PROJECT_SPEC·AUTH_DESIGN의 확정 정책으로 UserRepository·AuthService·AuthController·인증 DTO·SessionUser·SecurityConfig·LoginRateLimiter와 공통 JSON 오류 처리를 추가했다. Spring Security starter 및 security-test starter만 추가하고 Java·Boot·Wrapper 버전과 V1~V3는 유지한다.
- DaoAuthenticationProvider/ProviderManager와 PBKDF2 DelegatingPasswordEncoder를 사용한다. 모르는 계정에 대한 더미 해시 검증은 표준 provider가 수행한다. principal은 인증 성공 후 해시를 지우고 UUID·이메일만 세션에 남긴다.
- Controller 로그인 성공 시 ChangeSessionIdAuthenticationStrategy와 CsrfAuthenticationStrategy를 호출하고 SecurityContextRepository에 명시적으로 저장한다. CSRF는 기본 XOR 요청 처리기와 HttpSessionCsrfTokenRepository를 사용한다. /api/auth/csrf에서 받은 토큰을 헤더로 보내고 로그인·로그아웃 후 재조회한다.
- Spring Security LogoutFilter가 세션·인증·CSRF 정리와 JSESSIONID 삭제를 수행하며 JSON API에서 리다이렉트를 사용하지 않는다. 미인증·CSRF/접근 오류는 필터 계층에서도 동일한 code·message·details JSON으로 처리한다.
- 쿠키는 기본 Secure=true·HttpOnly·Lax·비활성 30분·cookie-only다. local 프로필의 로컬 HTTP에서만 Secure=false다. forwarded header는 초기에는 해석하지 않는다. credential CORS는 허용하지 않으며 프런트는 같은 출처 프록시를 사용할 예정이다.
- 메모리 로그인 제한은 직접 연결 IP/정규화 이메일을 키로 고정 5분 창·IP 20회·쌍 5회·최대 10000개 키를 적용한다. 만료 제거·Retry-After를 제공한다. 재시작 시 초기화되며 신뢰 프록시 설정과 다중 서버 지원은 배포 단계에서 별도 검토한다.
- Hibernate JDBC 오류 상세에는 중복 이메일이 들어갈 수 있어 해당 오류 로거를 비활성화했다. 응답·로그에 입력·인증·SQL 상세를 남기지 않는다.
검증: compileJava/compileTestJava 통과, AuthTests 7개(WebMvcTest·실제 보안 체인, UserRepository mock)와 LoginRateLimiterTests 3개(Clock 기반)는 Codex에서 실행해 총 10개 통과했다. 이후 사용자 실행의 21:58 로컬 XML로 DB 포함 전체 20개(기존 8+인증 HTTP 7+제한 3+등록 DB 2)·실패/오류/건너뜀 0을 확인했다. 사용자 21:58 서버 로그로 local 프로필·Repository 3개·Flyway 버전 3·8080 정상 기동을 확인했다. 사용자 실제 HTTP 가입·로그인·CSRF 재조회·me·로그아웃 후 401 흐름도 성공했다. 실제 HTTP의 세션 유지·로그아웃은 확인했으며 운영 HTTPS 쿠키 속성·30분 만료·브라우저·프록시는 별도 확인이 필요하다. [AUTH_GUIDE](AUTH_GUIDE.md)에 명령과 기대 결과를 기록했다.

## 카테고리 서비스·API 구현 (2026-10-20)
CreateCategoryRequest·RenameCategoryRequest·CategoryResponse·CategoryController·CategoryService를 추가했다. JSON은 허용 필드만 문자열로 받고 이름을 strip한 후 Bean Validation으로 검사한다. SessionUser.id를 서비스에 전달하고 쿼리에서 사용자 범위와 보관 필터·정렬을 적용한다. 등록·수정은 사전 중복 검사와 saveAndFlush의 명명된 UNIQUE 오류를 409로 처리한다. 삭제는 archive만 수행하며 이름 변경과 보관은 DynamicUpdate로 해당 컬럼만 갱신한다. 타입 변경·물리 삭제·복원 경로는 없다.
공통 오류 처리에 UUID·쿼리 타입 변환 실패 400을 추가했다. 기존 AuthService 주석 변경·미커밋 문서를 보존했으며 스키마·의존성·보안 설정을 바꾸지 않았다. 카테고리 API 구현으로 이전 미구현 기록을 갱신한다. 거래 서비스·API·409 변환은 여전히 미구현이다.
검증: CategoryWebTests 5개·CategoryServiceTests 2개와 기존 인증 10개, 총 17개를 Codex에서 실행해 통과했다. CategoryIntegrationTests 5개는 PostgreSQL과 실제 서비스/Repository/보안 필터를 사용하는 @SpringBootTest·@Transactional 테스트다. 두 사용자 테스트 세션은 fixture principal로 구성하며 실제 로그인 흐름은 기존 AuthTests와 수동 검증에 근거한다. 이후 Codex가 권한을 받아 별도 임시 PostgreSQL에서 전체 32개를 직접 실행해 실패/오류/건너뜀 0을 확인했다. DB 소유권·중복·보관·정렬·기존 거래 연결 유지와 기존 20개 회귀를 검증했다. bootJar 빌드 후 임시 local 서버에서 실제 HTTP 등록201·수정200·보관/반복204·보관 필터·로그아웃 후401도 통과했다. 임시 서버·DB를 정리했고 기존 사용자 DB·서버는 유지했다. 실행 가이드는 CATEGORY_API_GUIDE를 따른다.

## 카테고리 이름순 정렬 검증 (2026-10-20)
사용자 요청으로 CategoryRepository·CategoryService를 OrderByNameAscIdAsc로 변경했다. CategoryIntegrationTests는 생성 순서와 다른 한글 이름(가계·나들이·다과), 동일 이름의 수입/지출 ID 순서, 보관 포함/제외와 사용자 격리를 검증하도록 갱신했다. 임시 PostgreSQL에서 카테고리 12개·기존 Repository 4개, 총 16개 테스트를 직접 실행해 실패/오류/건너뜀 0으로 통과했다. 임시 DB는 정리했고 기존 DB·서버·collation·마이그레이션은 유지했다. 로컬 DB collation(en_US.utf8)의 읽기 전용 예시 조회에서도 가나다순을 확인했다. 보조 SHOW LC_COLLATE 진단 명령은 지원되지 않아 실패했으나 테스트는 정상 통과했으며 collation 값은 앞선 pg_database 조회 결과를 근거로 한다. 배포 환경 collation은 별도 검증한다.
