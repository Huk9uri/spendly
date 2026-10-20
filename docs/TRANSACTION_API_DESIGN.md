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
