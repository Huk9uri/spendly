# Spendly 프런트엔드

React·TypeScript·Vite·Tailwind·shadcn/ui 기반 로그인 및 거래 목록 화면입니다. 세션·CSRF를 사용하는 기존 백엔드와 동일 출처 개발 프록시로 연결합니다.

실행·환경변수·검증·후속 범위는 [프런트엔드 가이드](../docs/FRONTEND_GUIDE.md)를 따릅니다.

```powershell
npm ci
npm run dev
```

백엔드를 local 프로필로 별도 실행한 뒤 http://127.0.0.1:5173/login 에서 가입 계정으로 로그인합니다.

```powershell
npm run build
npm test
npm run lint
```

격리 E2E는 저장소 루트에서 `.\frontend\scripts\verify-e2e.ps1`로 실행합니다. Docker와 Playwright Chromium이 필요합니다. 기존 사용자 DB 대신 임시 DB를 사용합니다. `npm run test:e2e`를 직접 실행하면 지정 서버에 가짜 계정·거래가 추가되므로 격리 스크립트를 사용하세요.
