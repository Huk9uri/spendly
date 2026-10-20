# 인증 실행·검증 가이드

기준일: 2026-10-20. 확정 설계는 [PROJECT_SPEC](PROJECT_SPEC.md), 상세는 [AUTH_DESIGN](AUTH_DESIGN.md)을 따른다.

## 1. 전체 테스트
저장소 루트 PowerShell에서 실행한다. Docker Desktop·Spendly PostgreSQL이 실행 중이어야 하고 현재 창에 SPENDLY_DB_PASSWORD가 있어야 한다.

```powershell
Test-Path Env:SPENDLY_DB_PASSWORD
```

False라면 기존 DB 비밀번호를 다시 입력한다(비밀번호 변경 명령이 아니다).

```powershell
$spendlyDbCredential = Get-Credential -UserName spendly -Message "기존 Spendly DB 비밀번호"
$env:SPENDLY_DB_PASSWORD = $spendlyDbCredential.GetNetworkCredential().Password
```

```powershell
.\backend\gradlew.bat -p backend test --rerun-tasks
```

기대 결과: 총 20개 테스트, 실패·오류·건너뜀 0과 BUILD SUCCESSFUL.
기존 8개 + AuthTests 7개(실제 보안 필터·MockMvc, UserRepository는 mock) + LoginRateLimiterTests 3개(Clock 기반 단위 테스트) + RegistrationTests 2개(실제 PostgreSQL).
DB 테스트 fixture는 롤백하며 HTTP 검증에 영구 회원가입 데이터를 만들지 않는다.

## 2. 로컬 HTTP 서버 실행
기존 서버는 Ctrl+C로 종료한다. 로컬 HTTP에서는 local 프로필을 반드시 지정한다. 기본 Secure 쿠키는 운영 HTTPS용이며 local에서만 해제된다.

```powershell
.\backend\gradlew.bat -p backend bootRun --args='--spring.profiles.active=local'
```

기대 결과: Repository 3개, Flyway 버전 3, 8080 정상 기동. 브라우저 기본 로그인 화면·기본 생성 비밀번호를 사용하지 않는다.

## 3. 실제 HTTP 가입·로그인·조회·로그아웃
서버를 켠 채 새 PowerShell을 연다. 여기서는 실제 로컬 테스트 계정이 DB에 남는다. 12~128자 비밀번호를 입력하며 비밀번호와 CSRF 토큰·세션 쿠키를 채팅에 붙여넣지 않는다.

```powershell
$spendlyAuthBase = 'http://localhost:8080'
$spendlyAuthSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$spendlyAuthCredential = Get-Credential -Message '테스트 가입할 이메일과 12~128자 비밀번호'
$spendlyAuthBody = @{
    email = $spendlyAuthCredential.UserName
    password = $spendlyAuthCredential.GetNetworkCredential().Password
} | ConvertTo-Json

$spendlyCsrf = Invoke-RestMethod "$spendlyAuthBase/api/auth/csrf" -WebSession $spendlyAuthSession
$spendlyAuthHeaders = @{}
$spendlyAuthHeaders[$spendlyCsrf.headerName] = $spendlyCsrf.token

$spendlyRegistered = Invoke-RestMethod "$spendlyAuthBase/api/auth/register" -Method Post -WebSession $spendlyAuthSession -Headers $spendlyAuthHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyAuthBody
$spendlyLoggedIn = Invoke-RestMethod "$spendlyAuthBase/api/auth/login" -Method Post -WebSession $spendlyAuthSession -Headers $spendlyAuthHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyAuthBody

# Login rotates the CSRF token. Discard the old token and fetch a fresh one.
$spendlyCsrf = Invoke-RestMethod "$spendlyAuthBase/api/auth/csrf" -WebSession $spendlyAuthSession
$spendlyAuthHeaders = @{}
$spendlyAuthHeaders[$spendlyCsrf.headerName] = $spendlyCsrf.token

$spendlyMe = Invoke-RestMethod "$spendlyAuthBase/api/me" -WebSession $spendlyAuthSession
$spendlyMe

Invoke-RestMethod "$spendlyAuthBase/api/auth/logout" -Method Post -WebSession $spendlyAuthSession -Headers $spendlyAuthHeaders

try {
    Invoke-RestMethod "$spendlyAuthBase/api/me" -WebSession $spendlyAuthSession
} catch {
    [int]$_.Exception.Response.StatusCode
}

Remove-Variable spendlyAuthBody, spendlyAuthCredential, spendlyCsrf, spendlyAuthHeaders
```

기대 결과: 가입 응답 201, 로그인·me 200(id·email만 반환), 로그아웃 204, 마지막 me는 401. CSRF를 빠뜨리거나 잘못 보내면 403이다. 기존 이메일로 재가입하면 409이며, 기존 계정이라면 register 명령을 생략하고 로그인한다.

## 4. 아직 별도로 확인할 사항
운영 HTTPS에서 Secure·HttpOnly·SameSite=Lax 쿠키, 실제 브라우저 쿠키 전송·비활성 만료 30분, 동일 사이트 프록시와 신뢰 프록시/IP 정책은 배포 때 검증한다. 로그인 제한은 서버 메모리에 있어 재시작 시 초기화된다. 초기 개발은 프런트 프록시로 동일 출처를 사용하며 credential CORS 출처는 허용하지 않는다.
