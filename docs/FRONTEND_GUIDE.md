# 프런트엔드 실행·검증 가이드

작성일: 2026-10-21. 기준: [PROJECT_SPEC](PROJECT_SPEC.md). 이번 범위는 초기 구성·로그인·거래 목록이다. 회원가입·거래 등록/수정/삭제 화면은 다음 작업이다.

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

build는 TypeScript 검사와 Vite 프로덕션 빌드다. test는 Vitest 10개로 입력·응답 검증, CSRF 재조회, 세션 만료, 로그인·로그아웃과 캐시 정리, 오류 재시도를 검사한다. 패키지 잠금 파일을 함께 관리한다. 초기 npm 10의 선택적 peer 해석 오류는 npm 11로 설치하고 resolver 5를 선택해 해결했으며, 최종 잠금 파일의 npm 10.8.2 `npm ci`도 통과했다. 이후 설치에서 같은 초기 해석 오류가 재현되면 강제 peer 무시 대신 `npx --yes npm@11 ci`를 사용한다.

## 격리된 실제 브라우저 E2E

Docker Desktop을 실행하고 최초 한 번 이미지를 준비한다. Playwright 브라우저도 최초 한 번 설치한다.

```powershell
docker pull postgres:16-alpine
cd C:\workspace\spendly\frontend
npx playwright install chromium
cd ..
.\frontend\scripts\verify-e2e.ps1
```

스크립트는 bootJar 빌드 후 임시 PostgreSQL·임시 local 백엔드·임시 Vite 서버를 임의 포트에 실행한다. 가짜 계정과 거래로 데스크톱 1280px·모바일 390px에서 2개 시나리오씩, 총 4개를 실행하고 해당 프로세스와 컨테이너를 정리한다. 기존 Spendly DB·8080 서버·5173 서버는 사용하지 않는다. 프로세스 범위 환경변수도 원래 값으로 복구한다. Windows PowerShell용이며 Docker·Gradle 캐시 접근이 필요하다.

검증 항목은 실제 로그인 실패/성공·세션/CSRF·목록·리터럴 검색·정렬·페이지·새로고침·최대 금액·로그아웃·계정 전환·오류 재시도·세션 만료다. 스크린샷은 `frontend/test-results`에 저장하고 Git에서 제외한다. 인증 추적 영상과 trace는 수집하지 않는다. `npm run test:e2e`를 직접 실행하면 지정 서버에 가짜 데이터를 추가하므로 이 격리 스크립트로 실행한다.

## UI 컴포넌트와 배포 범위

shadcn CLI 4.22.1의 Radix/nova 프리셋으로 Button·Input·Label·Card 소스를 추가했다. 입력 높이와 로그인 제목의 의미 구조, 녹색·밝은 배경 테마를 조정했다. 현재 네 컴포넌트에 필요하지 않은 CLI 패키지와 확장 CSS는 제거했다. UI 소스는 `src/components/ui`에서 관리한다. 추가 컴포넌트를 도입할 때 공식 CLI 변경 내용과 새 의존성을 검토한다.

참고: [shadcn Vite 설치](https://ui.shadcn.com/docs/installation/vite), [Vite 시작](https://vite.dev/guide/), [TanStack Query](https://tanstack.com/query/latest/docs/framework/react/overview), [React Hook Form 연동](https://ui.shadcn.com/docs/forms/react-hook-form).

`npm run preview`는 빌드 산출물 확인용이며 API 프록시 배포 구성이 아니다. 운영에서는 동일 출처 `/api` 리버스 프록시·SPA 경로 fallback·HTTPS·Secure 쿠키 설정을 별도로 검증한다. 개발 프록시 검증을 운영 배포 완료로 취급하지 않는다.


## 이번 작업의 검증 결과
2026-10-21 Codex 직접 실행: npm ci·build·Vitest 10개·lint 통과, 감사 취약점 0건. 격리 서버의 실제 Chromium 데스크톱·모바일 총 4개 E2E 통과 및 스크린샷 확인. 임시 자원 정리 완료. 상세 근거는 [ROADMAP](ROADMAP.md)을 따른다.
