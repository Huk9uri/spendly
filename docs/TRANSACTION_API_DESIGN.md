# 거래 등록·상세 조회 API 설계

작성일: 2026-10-21. 기준: [PROJECT_SPEC](PROJECT_SPEC.md).
상태: feat/transaction의 구현 요청과 메모 정책 수정 지시에 따라 구현했다. 메모 누락·null은 모두 빈 문자열이다. 임시 PostgreSQL 전체 48개와 실제 HTTP 검증을 완료했다.

## 범위
POST /api/transactions와 GET /api/transactions/{id}의 DTO·서비스·Controller·필요 Repository 보완·테스트·실행 가이드를 작성한다. 수정·삭제·목록·검색·페이지·거래 충돌 409 변환은 별도 작업이다. 스키마·버전·기존 설정은 유지한다.

## API 계약
- POST 성공은 201, GET 성공은 200. 공통 응답은 기준 문서 예시대로 id·type·amount·categoryId·occurredOn·description·memo·version이다. user_id와 엔티티는 노출하지 않는다.
- 생성 필수값은 type·amount·categoryId·occurredOn·description. 사용자 지시에 따라 memo 누락과 명시적 null은 모두 빈 문자열로 정규화한다. memo는 최대 1000자·입력 공백 유지다. description은 strip 후 빈 내용 금지·최대 100자다.
- type은 문자열 INCOME/EXPENSE. amount는 JSON 숫자의 정수 표기(예: 12000)만 허용하며 문자열 숫자·소수·지수 표기를 거부한다. BigDecimal로 정확히 읽고 1~999999999999999를 저장 전에 검증한다.
- occurredOn은 문자열 YYYY-MM-DD의 유효한 날짜(연도 0001~9999)로 받는다. 별도의 미래 날짜 금지 정책은 도입하지 않는다. categoryId·경로 id는 UUID 형식을 검증한다.
- 알려지지 않은 필드와 클라이언트 userId/user_id·id·version·시각 지정은 400으로 거부한다. 사용자 ID는 인증 principal에서만 얻는다.
- 없는/타인 소유 거래의 상세는 동일한 404 TRANSACTION_NOT_FOUND. 없는/타인 카테고리로 등록 시 동일한 404 CATEGORY_NOT_FOUND. 유형 불일치는 400 CATEGORY_TYPE_MISMATCH, 보관 카테고리는 400 CATEGORY_ARCHIVED로 처리한다.
- 미인증 401·CSRF 오류 403을 유지한다. 기존 필터의 CSRF 우선 검사는 그대로다. 새 거래 version은 0이며 수정 API와 HTTP 충돌 409는 이번에 구현하지 않는다.

## 구현·검증 방향
등록 트랜잭션에서 카테고리 사용자·유형·보관 상태를 검사한다. 카테고리 확인과 동시에 보관하는 경쟁 상황은 해당 PESSIMISTIC_READ 행 잠금으로 등록 트랜잭션 종료까지 보호한다. 기존 거래는 카테고리가 보관돼도 상세 조회할 수 있다.
HTTP 입력·보안 계약과 서비스 실패 케이스, 실제 PostgreSQL의 정수 금액·날짜 저장/재조회·다른 사용자 접근 차단·보관/유형 검사·기존 회귀를 검증한다. 별도 임시 DB·임시 서버를 사용할 수 있으면 실제 HTTP도 확인하고 정리한다. 확정 계약과 구현 결과를 PROJECT_SPEC과 관련 문서에 반영했다. 실제 명령은 TRANSACTION_API_GUIDE를 따른다.

## 거래 목록·수정·삭제 확정 설계 (2026-10-21)
사용자가 목록·수정·삭제 구현을 요청했다. 사용자가 제안을 승인했다. 다음 계약을 PROJECT_SPEC과 관련 문서에 함께 반영하고 구현한다.

### 목록
- GET /api/transactions는 인증 사용자 범위에서 month·type·categoryId·q를 AND 조건으로 적용한다. 필터 누락은 해당 조건을 적용하지 않으며 month 누락 시 전체 기간이다.
- month는 YYYY-MM(연도 0001~9999), 해당 월 첫날 이상·다음 월 첫날 미만의 DATE 범위다. type은 INCOME/EXPENSE, categoryId는 표준 UUID다. 유효한 UUID지만 데이터가 없거나 타인 카테고리인 필터는 빈 목록을 반환한다.
- q는 strip 후 최대 100자, 빈 값은 필터 없음이다. description·memo에서 대소문자 구분 없이 부분 검색하며 %, _, 역슬래시는 와일드카드가 아닌 실제 문자로 취급한다.
- page는 0부터, 기본 0. size는 1~100, 기본 20. sort는 필드,방향 문자열 하나로 occurredOn/amount/createdAt과 asc/desc만 허용한다. 기본 occurredOn,desc이며 id를 같은 방향의 보조 정렬로 사용한다. 미지원 값은 400이다.
- 200 응답은 {content:[거래 DTO],page,size,totalElements,totalPages}. 없으면 content=[]이며 totalElements/totalPages=0이다. 입력되지 않은 보조 필터·페이지 계약은 추가하지 않는다.

### 수정
- PATCH /api/transactions/{id}의 JSON에 비음수 정수 version을 필수로 전달한다. 최소 1개의 수정 필드가 필요하다. 성공은 200 거래 DTO다.
- 수정 허용 필드는 type·amount·categoryId·occurredOn·description·memo. 누락 필드는 기존 값을 유지한다. 명시적 memo:null은 '', 나머지 필드의 null은 400이다. 미지원 필드와 소유자/ID/시각 위조는 거부한다. 값 검증은 생성과 동일하다.
- 변경 후 카테고리와 거래의 소유자·유형이 일치해야 한다. 다른 카테고리를 선택할 때는 사용 중인 항목이어야 한다. 기존 카테고리 ID·유형을 유지하는 수정은 해당 카테고리가 논리 삭제됐어도 허용한다.
- 요청 version과 현재 version이 다르거나 실제 동시 수정이 발생하면 409 TRANSACTION_VERSION_CONFLICT. 실제 값 변경이 있으면 version이 증가한다. 요청한 값이 모두 기존과 같으면 200으로 현재 version을 그대로 반환한다. 오래된 version은 같은 값을 보내도 409다.

### 삭제
- DELETE /api/transactions/{id}는 인증 사용자 소유 거래를 물리 삭제하고 204를 반환한다. 카테고리는 변경하지 않는다. 요청 본문·version 전달은 요구하지 않는다.
- 없거나 타인 거래는 동일 404 TRANSACTION_NOT_FOUND. 따라서 삭제 후 반복 요청은 404다. JPA 버전 충돌이 삭제와 동시에 발생하면 공통 409로 처리한다.
- 수정/삭제에 세션·CSRF를 적용하고 기존 필터 우선 순서를 유지한다. 등록·상세 계약과 기존 스키마·버전·의존성은 유지한다.

### 검증 방향
목록 필터·검색 리터럴·정렬 동률·페이지 경계·합계와 사용자 격리, PATCH 누락/null·카테고리 보관·type 변경·소유권·version과 동시 수정, 물리 삭제·반복 삭제·타인 접근·CSRF를 검증한다. 기존 48개를 포함한 전체 회귀와 임시 DB·실제 HTTP를 확인하고 실행 가이드를 갱신한다.


## 목록·수정·삭제 검증 결과 (2026-10-21)
구현과 임시 PostgreSQL 전체 58개 테스트·실제 동시성 및 HTTP 검증을 완료했다. 실패·오류·건너뜀은 0이다. 임시 자원은 정리했으며 상세 근거는 ROADMAP, 재현 절차는 TRANSACTION_API_GUIDE에 기록했다.
