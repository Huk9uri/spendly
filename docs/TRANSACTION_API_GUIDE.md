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
