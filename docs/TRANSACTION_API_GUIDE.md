# 거래 등록·상세 조회 실행 가이드

기준일: 2026-10-21. 요구사항은 [PROJECT_SPEC](PROJECT_SPEC.md), 계약은 [TRANSACTION_API_DESIGN](TRANSACTION_API_DESIGN.md)을 따른다.

## 구현 범위와 검증 결과
- POST /api/transactions: 등록 201, GET /api/transactions/{id}: 상세 200.
- 메모 누락·null은 모두 빈 문자열로 저장한다. 문자열 메모의 공백은 유지한다.
- 기존 32개와 거래 HTTP 7·서비스 3·DB 통합 5·행 잠금 1, 전체 48개 테스트를 임시 PostgreSQL에서 직접 실행해 실패·오류·건너뜀 0을 확인했다.
- 별도 임시 local 서버에서 실제 세션·CSRF로 등록·상세·메모 누락/null·최대 금액·타인 404·보관 카테고리 등록 400·보관 후 기존 상세 200·미인증 401을 확인했다. 임시 서버와 DB는 정리했다.
- 이번 범위는 등록과 상세뿐이다. 목록·검색·페이지·수정·삭제·수정 충돌 409 API는 없다.

## 로컬 재현
기존 서버를 Ctrl+C로 종료하고 저장소 루트에서 실행한다. PostgreSQL과 SPENDLY_DB_PASSWORD 환경변수 준비는 [AUTH_GUIDE](AUTH_GUIDE.md)의 1번을 따른다.

```powershell
Set-Location C:\workspace\spendly
.\backend\gradlew.bat -p backend test --rerun-tasks
.\backend\gradlew.bat -p backend bootRun --args='--spring.profiles.active=local'
```

기대 결과: 테스트 48개 통과, Repository 3개와 Flyway 버전 3, local 프로필 8080 기동. 테스트 자체는 fixture를 롤백하거나 해당 fixture만 정리한다.

## 실제 가입된 계정으로 HTTP 재현
서버를 켠 채 새 PowerShell에서 실행한다. 기존 가입 계정으로 로그인하며, 아래 수동 실행은 실제 카테고리 1개와 거래 2개를 DB에 남긴다. 거래 삭제 API는 이번 범위에 없다. 비밀번호·쿠키·CSRF 토큰을 공유하지 않는다.

```powershell
$spendlyTxBase = 'http://localhost:8080'
$spendlyTxSession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$spendlyTxCredential = Get-Credential -Message '기존 Spendly 계정으로 로그인'
$spendlyTxLoginBody = @{
    email = $spendlyTxCredential.UserName
    password = $spendlyTxCredential.GetNetworkCredential().Password
} | ConvertTo-Json
$spendlyTxCsrf = Invoke-RestMethod "$spendlyTxBase/api/auth/csrf" -WebSession $spendlyTxSession
$spendlyTxHeaders = @{}
$spendlyTxHeaders[$spendlyTxCsrf.headerName] = $spendlyTxCsrf.token
$spendlyTxLogin = Invoke-RestMethod "$spendlyTxBase/api/auth/login" -Method Post -WebSession $spendlyTxSession -Headers $spendlyTxHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyTxLoginBody

# Refresh the token after login.
$spendlyTxCsrf = Invoke-RestMethod "$spendlyTxBase/api/auth/csrf" -WebSession $spendlyTxSession
$spendlyTxHeaders = @{}
$spendlyTxHeaders[$spendlyTxCsrf.headerName] = $spendlyTxCsrf.token
$spendlyTxCategoryBody = @{ name = 'transaction-check-' + (Get-Date -Format 'yyyyMMddHHmmssfff'); type = 'EXPENSE' } | ConvertTo-Json
$spendlyTxCategory = Invoke-RestMethod "$spendlyTxBase/api/categories" -Method Post -WebSession $spendlyTxSession -Headers $spendlyTxHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyTxCategoryBody

$spendlyTxBody = @{
    type = 'EXPENSE'
    amount = 12000
    categoryId = $spendlyTxCategory.id
    occurredOn = '2026-10-21'
    description = 'transaction verification'
    memo = $null
}
$spendlyTxNullMemo = Invoke-RestMethod "$spendlyTxBase/api/transactions" -Method Post -WebSession $spendlyTxSession -Headers $spendlyTxHeaders -ContentType 'application/json; charset=utf-8' -Body ($spendlyTxBody | ConvertTo-Json)
$spendlyTxBody.Remove('memo')
$spendlyTxMissingMemo = Invoke-RestMethod "$spendlyTxBase/api/transactions" -Method Post -WebSession $spendlyTxSession -Headers $spendlyTxHeaders -ContentType 'application/json; charset=utf-8' -Body ($spendlyTxBody | ConvertTo-Json)
$spendlyTxDetail = Invoke-RestMethod "$spendlyTxBase/api/transactions/$($spendlyTxNullMemo.id)" -WebSession $spendlyTxSession
[pscustomobject]@{
    nullMemoEmpty = $spendlyTxNullMemo.memo -ceq ''
    missingMemoEmpty = $spendlyTxMissingMemo.memo -ceq ''
    detailMatches = $spendlyTxDetail.id -eq $spendlyTxNullMemo.id
    versionIsZero = $spendlyTxDetail.version -eq 0
}

Invoke-RestMethod "$spendlyTxBase/api/auth/logout" -Method Post -WebSession $spendlyTxSession -Headers $spendlyTxHeaders
Remove-Variable spendlyTxCredential, spendlyTxLoginBody, spendlyTxCsrf, spendlyTxHeaders
```

기대 결과: 네 확인값 모두 True. 실패 시 오류 상태·code만 공유한다. 자동 테스트는 사용자 ID 위조·타인 거래/카테고리·잘못된 금액/날짜/유형·보관 선택을 검사한다. 기존 거래 상세는 카테고리 보관 후에도 조회할 수 있다.


## 거래 목록·수정·삭제 확인 (2026-10-21)
루트 `C:\workspace\spendly`에서 DB를 기동하고 비밀번호 환경변수를 설정한 터미널로 실행한다. 기존 local 서버가 실행 중이면 변경 코드를 적용하도록 재시작한다. bootRun은 서버가 떠 있는 동안 계속 실행되는 것이 정상이다.

```powershell
.\backend\gradlew.bat -p backend test
.\backend\gradlew.bat -p backend bootRun --args='--spring.profiles.active=local'
```

다른 PowerShell 터미널에서 기존 가입 계정으로 로그인한다. 테스트는 실제 거래를 생성·수정·삭제하므로 아래에서 만든 거래 ID만 사용한다.

```powershell
$spendlyBase = 'http://localhost:8080'
$spendlySession = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$spendlyCredential = Get-Credential -Message '기존 가입 계정 이메일과 비밀번호'
$spendlyLoginBody = @{ email = $spendlyCredential.UserName; password = $spendlyCredential.GetNetworkCredential().Password } | ConvertTo-Json
$spendlyCsrf = Invoke-RestMethod "$spendlyBase/api/auth/csrf" -WebSession $spendlySession
$spendlyHeaders = @{}; $spendlyHeaders[$spendlyCsrf.headerName] = $spendlyCsrf.token
Invoke-RestMethod "$spendlyBase/api/auth/login" -Method Post -WebSession $spendlySession -Headers $spendlyHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyLoginBody
$spendlyCsrf = Invoke-RestMethod "$spendlyBase/api/auth/csrf" -WebSession $spendlySession
$spendlyHeaders = @{}; $spendlyHeaders[$spendlyCsrf.headerName] = $spendlyCsrf.token
Remove-Variable spendlyCredential, spendlyLoginBody

# 사용 중인 지출 카테고리 선택. 없다면 카테고리 가이드에 따라 먼저 만든다.
$spendlyCategories = Invoke-RestMethod "$spendlyBase/api/categories" -WebSession $spendlySession
$spendlyCategory = $spendlyCategories | Where-Object { $_.type -eq 'EXPENSE' } | Select-Object -First 1
if (-not $spendlyCategory) { throw '사용 중인 지출 카테고리가 필요합니다.' }
$spendlyBody = @{ type = 'EXPENSE'; amount = 12000; categoryId = $spendlyCategory.id; occurredOn = '2026-10-21'; description = '거래 API 확인'; memo = '테스트' } | ConvertTo-Json
$spendlyCreated = Invoke-RestMethod "$spendlyBase/api/transactions" -Method Post -WebSession $spendlySession -Headers $spendlyHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyBody
$spendlyPath = "$spendlyBase/api/transactions/$($spendlyCreated.id)"

# 목록: 전체 건수와 페이지 수, content를 확인한다.
$spendlyPage = Invoke-RestMethod "$spendlyBase/api/transactions?month=2026-10&type=EXPENSE&size=20&sort=amount,desc" -WebSession $spendlySession
$spendlyPage

# 부분 수정: memo만 비우고 다른 값은 유지한다. version은 0 -> 1.
$spendlyPatch = @{ version = $spendlyCreated.version; memo = $null } | ConvertTo-Json
$spendlyUpdated = Invoke-RestMethod $spendlyPath -Method Patch -WebSession $spendlySession -Headers $spendlyHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyPatch
$spendlyUpdated

# 같은 오래된 version 재전송: 409가 기대 결과다.
try {
    Invoke-RestMethod $spendlyPath -Method Patch -WebSession $spendlySession -Headers $spendlyHeaders -ContentType 'application/json; charset=utf-8' -Body $spendlyPatch
} catch { [int]$_.Exception.Response.StatusCode }

# 삭제 성공은 204(출력 없음), 삭제 후 상세·반복 삭제는 404다.
Invoke-RestMethod $spendlyPath -Method Delete -WebSession $spendlySession -Headers $spendlyHeaders
try { Invoke-RestMethod $spendlyPath -WebSession $spendlySession } catch { [int]$_.Exception.Response.StatusCode }
try { Invoke-RestMethod $spendlyPath -Method Delete -WebSession $spendlySession -Headers $spendlyHeaders } catch { [int]$_.Exception.Response.StatusCode }
Invoke-RestMethod "$spendlyBase/api/auth/logout" -Method Post -WebSession $spendlySession -Headers $spendlyHeaders
Remove-Variable spendlyCsrf, spendlyHeaders
```

수정은 최소 한 필드와 최신 version이 필요하다. 누락은 유지, memo:null은 빈 문자열, 그 외 null은 400이다. 동일 값 수정은 version이 유지된다. 충돌 시 상세를 다시 읽고 변경 내용을 확인한 뒤 최신 version으로 수정한다. 검색어에 특수 문자를 넣을 때는 `[uri]::EscapeDataString($검색어)`로 URL 인코딩한다. 금액은 정수 원화다.
