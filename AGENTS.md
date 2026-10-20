# Spendly 작업 지침

## 기준 문서와 작업 시작
- 프로젝트 요구사항의 기준은 [PROJECT_SPEC.md](docs/PROJECT_SPEC.md)이다. 새로운 Codex 스레드를 포함한 모든 작업에서 이 파일과 PROJECT_SPEC 전체를 먼저 읽는다.
- 이어서 [PRD](docs/PRD.md), [아키텍처](docs/ARCHITECTURE.md), [DB 설계](docs/DB_DESIGN.md), [API 명세](docs/API_SPEC.md), [로드맵](docs/ROADMAP.md)을 읽고 관련 현재 코드·테스트와 대조한다.
- `git status --short`, `backend/build.gradle`, `backend/settings.gradle`, `backend/src/main/resources/application.properties`, Gradle Wrapper 설정 및 하위 AGENTS.md를 확인한다. 기존 사용자 변경을 보존한다.
- 요구사항은 PROJECT_SPEC을, 구현 여부는 실제 코드를 근거로 판단한다. 차이가 있으면 보고하고 기록하며, 요구사항이나 코드를 임의로 덮어써서 일치시키지 않는다.
- 모호한 결정은 구현 전에 질문하거나 선택지를 제시한다. 확정된 설계 변경은 PROJECT_SPEC과 관련 문서·로드맵을 같은 작업에서 함께 갱신하고 변경 이유와 날짜를 남긴다. 파생 문서만으로 요구사항을 변경하지 않는다.

## 제품과 설계 원칙
- 모바일 우선 개인 수입·지출, 월 예산, 정기결제 예정일 관리 서비스다. 1인 개발, 주 15~20시간, 6주(90~120시간)가 초기 계획이다.
- 목표 기술은 React·TypeScript·Vite, TanStack Query, React Hook Form, Zod, Tailwind와 Java 17·Spring Boot·Spring Security·JPA, PostgreSQL·Flyway·Docker다. 테스트는 JUnit·Vitest·Playwright다. 목표 기술의 기재가 설치·구현 완료를 의미하지 않는다.
- 현재 선언된 백엔드 설정은 Java 17, Spring Boot 4.0.8, Gradle Wrapper 9.8.1이다. 버전이나 기존 설정을 임의로 변경하지 않는다.
- 금액은 정수 원화이며 클라이언트 Number 안전 정수 범위를 검증한다. 서버는 BigDecimal 또는 Long으로 정확히 계산한다.
- 모든 소유 리소스는 인증 사용자 ID로 필터링하고 클라이언트 user_id를 신뢰하지 않는다. 거래·카테고리의 소유자와 유형이 일치해야 한다.
- 거래가 있는 카테고리는 삭제 대신 보관 처리한다. 구독은 예정 정보만 저장하고 실제 거래를 자동 생성하지 않는다. PAUSED는 예정 결제에서 제외하며 월말 결제일을 보정한다.
- 거래 수정은 version 기반 낙관적 잠금과 409 충돌을 적용한다. 발생일은 DATE, 초기 시간대는 Asia/Seoul이다.
- 세션 쿠키 HttpOnly·운영 Secure·SameSite 정책, CSRF 방어, 비밀번호 해시, 로그인 속도 제한, CORS 출처 제한을 따른다. 개인정보·인증값·비밀 값을 코드나 로그에 남기지 않는다.

## 작업과 검증
- 작은 기능 단위로 설계→구현→검증→설명한다. 요청 범위 밖의 기능·의존성·설정 변경을 하지 않는다.
- 백엔드 테스트는 루트에서 `.\backend\gradlew.bat -p backend test` 또는 `./backend/gradlew -p backend test`로 실행한다. 프런트엔드 명령은 실제 프로젝트 생성 후 확인하여 문서화한다.
- 테스트 소스 존재와 통과를 구분하고 실행 여부·결과·미실행 이유를 보고한다. 문서 작업은 링크·코드·설정 대조로 검증할 수 있다.
- 로드맵은 완료·진행 중·예정을 유지한다. 시작 시 진행 중으로 이동하고 필요한 구현과 검증이 끝난 뒤 완료로 이동한다. 날짜·근거·차단 사항을 기록하고 개발 진행에 따라 갱신한다.

## Git과 보고
- 기존 Git 설정·원격·훅·사용자 파일을 임의로 변경하거나 사용자 변경을 되돌리지 않는다.
- 커밋과 푸시는 사용자 승인 후 진행한다. 현재 문서 정리 작업에서는 서비스 구현·커밋·푸시를 하지 않으며 완료 보고 후 사용자 확인을 기다린다.
- 모든 Git 커밋 메시지는 Conventional Commits 형식으로 **접두어만 영어**, 나머지 범위·설명·본문·꼬리말은 한국어로 작성한다.
- 접두어 예: feat, fix, refactor, test, docs, chore, build, ci, perf, style, revert. 기본 형식은 `docs: 프로젝트 요구사항과 개발 규칙 정리`다. 범위가 필요하면 `feat(거래): 거래 등록 기능 구현`처럼 한국어로 쓴다. 주요 변경은 `!`로 표시하고 설명은 한국어로 쓴다.
- 종료 시 변경 파일·내용, 문서와 코드의 차이, 검증 결과 및 추천 커밋 메시지를 보고한다.
