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
