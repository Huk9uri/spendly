# 카테고리 API 실행·검증 가이드

기준일: 2026-10-20. 확정 계약은 [PROJECT_SPEC](PROJECT_SPEC.md)과 [CATEGORY_API_DESIGN](CATEGORY_API_DESIGN.md)을 따른다.

## 전체 테스트와 서버 기동
기존 서버를 Ctrl+C로 종료하고 저장소 루트에서 실행한다. PostgreSQL 실행과 SPENDLY_DB_PASSWORD 설정이 필요하다. 설정 방법은 [AUTH_GUIDE](AUTH_GUIDE.md)의 1번을 따른다.

```powershell
Set-Location C:\workspace\spendly
.\backend\gradlew.bat -p backend test --rerun-tasks
```

기대 결과: 총 32건·실패/오류/건너뜀 0과 BUILD SUCCESSFUL. 기존 20개 + CategoryWebTests 5개 + CategoryServiceTests 2개 + CategoryIntegrationTests 5개다. 테스트 fixture는 롤백한다.

```powershell
.\backend\gradlew.bat -p backend bootRun --args='--spring.profiles.active=local'
```

Repository 3개, Flyway 버전 3, 8080 정상 기동을 확인한다. 스키마 변경은 없다.

## 실제 HTTP 흐름
서버를 켠 채 새 PowerShell에서 실행한다. 이미 가입한 이메일·비밀번호로 로그인한다. 이 가이드는 실제 테스트 카테고리 하나를 만들고 마지막에 보관한다. 물리 삭제하지 않는다. 비밀번호·CSRF·쿠키를 출력하거나 공유하지 않는다.

```powershell
$spendlyCategoryBase = 'http://localhost:8080'
$spendlyCategorySession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$spendlyCategoryCredential = Get-Credential -Message '기존 Spendly 계정으로 로그인'
$spendlyCategoryLoginBody = @{
    email = $spendlyCategoryCredential.UserName
    password = $spendlyCategoryCredential.GetNetworkCredential().Password
} | ConvertTo-Json
$spendlyCategoryCsrf = Invoke-RestMethod "$spendlyCategoryBase/api/auth/csrf" -WebSession $spendlyCategorySession
$spendlyCategoryHeaders = @{}
$spendlyCategoryHeaders[$spendlyCategoryCsrf.headerName] = $spendlyCategoryCsrf.token
$spendlyCategoryLogin = Invoke-RestMethod "$spendlyCategoryBase/api/auth/login" -Method Post -WebSession $spendlyCategorySession -Headers $spendlyCategoryHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyCategoryLoginBody

# Login clears the old CSRF token.
$spendlyCategoryCsrf = Invoke-RestMethod "$spendlyCategoryBase/api/auth/csrf" -WebSession $spendlyCategorySession
$spendlyCategoryHeaders = @{}
$spendlyCategoryHeaders[$spendlyCategoryCsrf.headerName] = $spendlyCategoryCsrf.token
$spendlyCategoryName = 'api-check-' + (Get-Date -Format 'yyyyMMddHHmmssfff')
$spendlyCategoryCreateBody = @{ name = $spendlyCategoryName; type = 'EXPENSE' } | ConvertTo-Json
$spendlyCategoryCreated = Invoke-RestMethod "$spendlyCategoryBase/api/categories" -Method Post -WebSession $spendlyCategorySession -Headers $spendlyCategoryHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyCategoryCreateBody
$spendlyCategoryId = $spendlyCategoryCreated.id
$spendlyCategoryRenameBody = @{ name = $spendlyCategoryName + '-renamed' } | ConvertTo-Json
$spendlyCategoryRenamed = Invoke-RestMethod "$spendlyCategoryBase/api/categories/$spendlyCategoryId" -Method Patch -WebSession $spendlyCategorySession -Headers $spendlyCategoryHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyCategoryRenameBody

Invoke-RestMethod "$spendlyCategoryBase/api/categories/$spendlyCategoryId" -Method Delete -WebSession $spendlyCategorySession -Headers $spendlyCategoryHeaders
Invoke-RestMethod "$spendlyCategoryBase/api/categories/$spendlyCategoryId" -Method Delete -WebSession $spendlyCategorySession -Headers $spendlyCategoryHeaders
$spendlyCategoryActive = Invoke-RestMethod "$spendlyCategoryBase/api/categories" -WebSession $spendlyCategorySession
$spendlyCategoryAll = Invoke-RestMethod "$spendlyCategoryBase/api/categories?includeArchived=true" -WebSession $spendlyCategorySession
[pscustomobject]@{
    renamed = $spendlyCategoryRenamed.name -eq ($spendlyCategoryName + '-renamed')
    hiddenFromActive = @($spendlyCategoryActive | Where-Object { $_.id -eq $spendlyCategoryId }).Count -eq 0
    archived = ($spendlyCategoryAll | Where-Object { $_.id -eq $spendlyCategoryId } | Select-Object -First 1).archived
}

Invoke-RestMethod "$spendlyCategoryBase/api/auth/logout" -Method Post -WebSession $spendlyCategorySession -Headers $spendlyCategoryHeaders
Remove-Variable spendlyCategoryCredential, spendlyCategoryLoginBody, spendlyCategoryCsrf, spendlyCategoryHeaders
```

기대 결과: renamed·hiddenFromActive·archived가 모두 True. HTTP 생성 201, 수정·조회 200, 첫 보관·반복 보관 204다. 오류 없이 완료됐는지와 세 확인값만 공유하면 된다.

중복 이름 409, 타인/미존재 카테고리 수정·보관 404, 필수값·UUID·알 수 없는 입력 필드 400, 미인증 401·CSRF 403은 자동 테스트에서 확인한다. 실제 DB·전체 회귀·실제 HTTP 실행 여부는 ROADMAP에 별도로 기록한다.

## 이번 구현의 실행 결과 (2026-10-20)
Codex에서 별도 임시 PostgreSQL로 전체 32개 테스트를 실행해 실패·오류·건너뜀 0을 확인했다. bootJar와 숨김 local 서버로 실제 HTTP 등록·수정·보관·필터·로그아웃 후 401도 통과했다. 임시 서버·DB는 정리했다. 위 명령은 사용자의 기존 로컬 환경에서도 재현하고 싶을 때 사용하며, 기존 DB에는 수동 HTTP 테스트 카테고리가 남는다.
