# 프런트엔드 실행·검증 가이드

작성일: 2026-10-21. 기준: [PROJECT_SPEC](PROJECT_SPEC.md). 초기 구성·로그인·거래 목록에 이어 카테고리 관리·거래 등록 화면을 추가했다. 회원가입·거래 수정/삭제 화면은 다음 작업이다.

## 준비

- Node.js: package.json의 engines 기준 20.19 이상 20.x 또는 22.12 이상. 이번 실행 환경은 20.19.6이다.
- Java 17, Docker Desktop, Spendly PostgreSQL. 기존 백엔드 설정·DB 비밀번호는 유지한다.
- 기존 가입 계정이 필요하다. 없으면 [인증 가이드](AUTH_GUIDE.md)로 가입한다. DB에 거래가 없으면 빈 목록이 정상이다. [거래 가이드](TRANSACTION_API_GUIDE.md)로 만든 거래도 화면에 표시된다.

## 로컬 실행

PowerShell 첫 번째 터미널, `C:\workspace\spendly`에서:

```powershell
docker compose -p spendly -f compose.yaml up -d --wait
# 기존 방식으로 이 터미널에 SPENDLY_DB_PASSWORD를 설정한다.
.\backend\gradlew.bat -p backend bootRun --args='--spring.profiles.active=local'
```

bootRun은 서버가 켜진 동안 계속 실행된다. 다른 터미널에서:

```powershell
cd C:\workspace\spendly\frontend
npm ci
npm run dev
```

[로그인 화면](http://127.0.0.1:5173/login)을 연다. 기본 5173 포트는 고정이며 사용 중이면 Vite가 종료된다. 사용자가 실행 중인 서버를 임의 종료하지 말고 해당 터미널을 확인한다. 서버 종료는 각 터미널의 Ctrl+C다.

백엔드는 local 프로필로 실행해야 로컬 HTTP 세션 쿠키를 사용할 수 있다. 프런트는 상대 경로 `/api`만 요청하며 Vite가 `http://127.0.0.1:8080`으로 프록시한다. 별도 CORS 허용은 추가하지 않는다. 백엔드 포트가 다르면 `frontend/.env.example`을 `.env.local`로 복사해 `SPENDLY_API_TARGET`을 바꾸고 Vite를 재시작한다. `VITE_*` 환경변수에 비밀번호나 토큰을 넣지 않는다.

## 화면 확인 순서

1. 미인증 `/transactions` 접근이 `/login`으로 이동하는지 확인한다.
2. 잘못된 이메일·12자 미만 비밀번호는 화면에서 입력 오류를 표시한다. 비밀번호의 공백·대소문자를 변환하지 않는다.
3. 가입 계정으로 로그인하면 거래 목록으로 이동한다. 새로고침 후에도 서버 세션이 유지된다.
4. 월 누락은 전체 기간이다. 유형·카테고리·검색어·정렬을 선택하고 페이지를 이동한다. 조건 변경 시 첫 페이지로 돌아간다. 검색은 버튼이나 Enter로 적용하며 URL에 조건을 유지한다.
5. 보관된 카테고리도 기존 거래를 찾을 수 있도록 조회 필터에 표시한다. 등록·수정 폼의 선택 정책과는 별개다. 발생일은 DATE 문자열을 그대로 표시해 시간대 변환으로 날짜가 바뀌지 않는다.
6. 목록 없음·요청 실패·재시도 상태를 확인한다. 로그아웃 후 로그인 화면으로 돌아가고 이전 계정 조회 캐시를 비운다. 세션 만료 응답 401도 같은 정리를 수행한다.

## 프런트엔드 검사

`frontend`에서:

```powershell
npm ci
npm run build
npm test
npm run lint
```

build는 TypeScript 검사와 Vite 프로덕션 빌드다. test는 Vitest 19개로 입력·응답 검증, CSRF 재조회, 세션 만료, 로그인·로그아웃과 캐시 정리, 오류 재시도를 검사한다. 패키지 잠금 파일을 함께 관리한다. 초기 npm 10의 선택적 peer 해석 오류는 npm 11로 설치하고 resolver 5를 선택해 해결했으며, 최종 잠금 파일의 npm 10.8.2 `npm ci`도 통과했다. 이후 설치에서 같은 초기 해석 오류가 재현되면 강제 peer 무시 대신 `npx --yes npm@11 ci`를 사용한다.

## 격리된 실제 브라우저 E2E

Docker Desktop을 실행하고 최초 한 번 이미지를 준비한다. Playwright 브라우저도 최초 한 번 설치한다.

```powershell
docker pull postgres:16-alpine
cd C:\workspace\spendly\frontend
npx playwright install chromium
cd ..
.\frontend\scripts\verify-e2e.ps1
```

스크립트는 bootJar 빌드 후 임시 PostgreSQL·임시 local 백엔드·임시 Vite 서버를 임의 포트에 실행한다. 가짜 계정과 거래로 데스크톱 1280px·모바일 390px에서 4개 시나리오씩, 총 8개를 실행하고 해당 프로세스와 컨테이너를 정리한다. 기존 Spendly DB·8080 서버·5173 서버는 사용하지 않는다. 프로세스 범위 환경변수도 원래 값으로 복구한다. Windows PowerShell용이며 Docker·Gradle 캐시 접근이 필요하다.

검증 항목은 실제 로그인 실패/성공·세션/CSRF·목록·리터럴 검색·정렬·페이지·새로고침·최대 금액·로그아웃·계정 전환·오류 재시도·세션 만료다. 스크린샷은 `frontend/test-results`에 저장하고 Git에서 제외한다. 인증 추적 영상과 trace는 수집하지 않는다. `npm run test:e2e`를 직접 실행하면 지정 서버에 가짜 데이터를 추가하므로 이 격리 스크립트로 실행한다.

## UI 컴포넌트와 배포 범위

shadcn CLI 4.22.1의 Radix/nova 프리셋으로 Button·Input·Label·Card 소스를 추가했다. 입력 높이와 로그인 제목의 의미 구조, 녹색·밝은 배경 테마를 조정했다. 현재 네 컴포넌트에 필요하지 않은 CLI 패키지와 확장 CSS는 제거했다. UI 소스는 `src/components/ui`에서 관리한다. 추가 컴포넌트를 도입할 때 공식 CLI 변경 내용과 새 의존성을 검토한다.

참고: [shadcn Vite 설치](https://ui.shadcn.com/docs/installation/vite), [Vite 시작](https://vite.dev/guide/), [TanStack Query](https://tanstack.com/query/latest/docs/framework/react/overview), [React Hook Form 연동](https://ui.shadcn.com/docs/forms/react-hook-form).

`npm run preview`는 빌드 산출물 확인용이며 API 프록시 배포 구성이 아니다. 운영에서는 동일 출처 `/api` 리버스 프록시·SPA 경로 fallback·HTTPS·Secure 쿠키 설정을 별도로 검증한다. 개발 프록시 검증을 운영 배포 완료로 취급하지 않는다.


## 이번 작업의 검증 결과
2026-10-21 Codex 직접 실행: npm ci·build·Vitest 10개·lint 통과, 감사 취약점 0건. 격리 서버의 실제 Chromium 데스크톱·모바일 총 4개 E2E 통과 및 스크린샷 확인. 임시 자원 정리 완료. 상세 근거는 [ROADMAP](ROADMAP.md)을 따른다.


## 카테고리 관리 → 첫 거래 등록 (2026-10-21)
1. IntelliJ에서 local 백엔드, Cursor에서 npm run dev를 실행하고 로그인한다. Docker PostgreSQL이 먼저 준비되어야 한다.
2. 거래 목록의 **카테고리 관리**를 누른다. 이름과 지출/수입 유형을 선택해 **카테고리 추가**를 누른다. 같은 사용자·유형에 같은 이름(삭제된 이름 포함)은 오류이며 입력은 유지된다.
3. 카테고리 행의 **이름 변경**으로 이름을 저장한다. **삭제 → 취소**는 유지, **삭제 → 삭제 확인**은 새 거래 선택에서 제외한다. **삭제된 카테고리 포함**을 켜면 기존 항목을 확인하고 이름을 수정할 수 있다. 유형 변경/복원은 제공하지 않는다.
4. **거래 등록으로 이동** 또는 목록의 **거래 등록**을 누른다. 유형에 맞는 사용 중인 카테고리만 표시된다. 유형 변경 시 카테고리를 다시 선택해야 한다.
5. 금액은 쉼표·소수·지수 없이 숫자만 입력한다. 발생일 기본값은 서울 기준 오늘이며 수정할 수 있다. 설명은 필수이고 메모는 선택이다.
6. 선택할 카테고리가 없다면 **카테고리 관리 (새 탭)**에서 추가한 뒤 원래 탭의 **카테고리 새로고침**을 누른다. 작성 중인 다른 값은 유지된다.
7. **거래 저장**을 누르면 성공 후 전체 기간 목록으로 돌아와 완료 메시지를 표시한다. 등록한 날짜·설명·정수 원화 금액을 확인한다. 작성 중 실패하면 입력을 유지하며 다시 저장할 수 있다.
8. 거래에 연결된 카테고리를 삭제한 뒤에도 기존 거래와 이름이 목록에 표시되는지 확인한다. 새 거래에서는 해당 카테고리가 제외된다.

신규 테스트는 금액·날짜·길이·타입/삭제 카테고리 필터·실패 입력 유지·등록 후 캐시 무효화·이중 제출 방지를 확인한다. 기존 격리 E2E 스크립트가 새 시나리오도 실행한다. 최종 실행 결과는 ROADMAP을 따른다.


## Docker 임시 통신 소켓 오류 복구 기록 (2026-10-21)
이번 브라우저 검증 중 Docker Desktop이 `dockerInference`, 이어 `docker-secrets-engine/engine.sock`의 Windows 접근 오류 1920으로 기동하지 못했다. [Docker 이슈 #531](https://github.com/docker/desktop-feedback/issues/531)의 같은 종류 보고를 참고해 임시 통신 폴더를 보존하는 방법을 적용했다.
- 정상 종료 명령이 실패해 시작 오류 상태의 Docker Desktop/backend 프로세스만 종료했다. `%LOCALAPPDATA%/Docker/run`과 `engine.sock`만 있는 `%LOCALAPPDATA%/docker-secrets-engine`의 실제 경로와 내용을 확인했다.
- 폴더를 삭제하지 않고 각각 `.spendly-backup-20261021-161003` 접미사로 이름을 바꿨다. 첫 시도의 `Docker/run.spendly-backup-20261021-160545`도 보존했다. DB·이미지·볼륨·Docker 설정·비밀값은 읽거나 변경하지 않았다.
- Docker 재기동 후 엔진 29.1.3 응답을 확인하고 기존 `spendly-postgres-1`의 프로젝트 라벨을 확인해 다시 시작했다. 임시 E2E DB는 별도로 사용한다.
- 이 조치는 이번 기동의 복구다. 같은 소켓 문제가 재발할 수 있으며 Docker 자체의 재시작 안정성이나 영구 수정을 확인한 것으로 취급하지 않는다. 백업 폴더는 자동 삭제하지 않는다.


## 카테고리 관리·거래 등록 검증 결과 (2026-10-21)
Codex 직접 실행: TypeScript/Vite build·Vitest 19개·lint와 임시 DB의 실제 Chromium E2E 8개 통과. 데스크톱·모바일 화면 확인 및 임시 테스트 자원 정리 완료. 기존 Spendly PostgreSQL healthy 확인. 자세한 실행 근거는 ROADMAP을 따른다.
