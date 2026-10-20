# Spendly API 명세

기준일: 2026-10-20. 기준: [PROJECT_SPEC](PROJECT_SPEC.md). 아래는 요구 API이며 현재 호출 가능한 업무 엔드포인트는 구현되어 있지 않다.

## 공통 계약
기본 경로는 `/api`다. 리소스 ID는 UUID, 금액은 JSON 숫자로 전달하는 정수 원화이며 클라이언트 Number 안전 정수 범위를 검증한다. 날짜는 YYYY-MM-DD, 월은 YYYY-MM이다. 세션 쿠키 인증과 CSRF 방어를 적용하며 모든 소유 리소스는 인증 사용자 ID로 필터링한다. 클라이언트 user_id는 신뢰하지 않는다.

## 인증·사용자
| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| POST | `/api/auth/register` | 가입 |
| POST | `/api/auth/login` | 로그인 |
| POST | `/api/auth/logout` | 로그아웃 |
| GET | `/api/me` | 현재 사용자 |

## 카테고리
| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| GET | `/api/categories` | 목록 |
| POST | `/api/categories` | 등록 |
| PATCH | `/api/categories/{id}` | 수정 |
| DELETE | `/api/categories/{id}` | 보관 처리(archive) |

## 거래
| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| GET | `/api/transactions?month=YYYY-MM&type=&categoryId=&q=&page=&size=&sort=` | 월·유형·카테고리·검색어 필터, 정렬·페이지네이션 |
| POST | `/api/transactions` | 생성 |
| GET | `/api/transactions/{id}` | 상세 |
| PATCH | `/api/transactions/{id}` | 수정, version 기반 낙관적 잠금 |
| DELETE | `/api/transactions/{id}` | 삭제 |

생성 예시(원본의 uuid는 실제 UUID 자리 표시자):
```json
{"type":"EXPENSE","amount":12000,"categoryId":"uuid","occurredOn":"2026-10-20","description":"점심","memo":""}
```

응답 예시:
```json
{"id":"uuid","type":"EXPENSE","amount":12000,"categoryId":"uuid","occurredOn":"2026-10-20","description":"점심","memo":"","version":0}
```

거래 카테고리는 인증 사용자 소유이며 type이 일치해야 한다. 수정 충돌은 409다. 수정 요청에서 version을 전달하는 구체적 계약은 구현 전에 확정한다.

## 예산
| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| GET | `/api/budgets/{month}` | 사용자·월 예산 조회 |
| PUT | `/api/budgets/{month}` | 사용자·월 예산 설정 |

## 정기결제
| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| GET | `/api/subscriptions` | 목록 |
| POST | `/api/subscriptions` | 등록 |
| PATCH | `/api/subscriptions/{id}` | 수정·일시정지 |
| DELETE | `/api/subscriptions/{id}` | 삭제 |
| GET | `/api/subscriptions/upcoming?from=&to=` | 예정 결제 조회 |

구독은 거래를 자동 생성하지 않는다. PAUSED는 예정 결제에서 제외하며 존재하지 않는 billing_day는 월말로 보정한다.

## 대시보드·통계
| 메서드 | 경로 | 용도 |
| --- | --- | --- |
| GET | `/api/dashboard?month=YYYY-MM` | 월별 대시보드 |
| GET | `/api/analytics/categories?month=YYYY-MM` | 카테고리별 집계 |
| GET | `/api/analytics/monthly?from=&to=` | 월별 집계 |

## 오류
```json
{"code":"VALIDATION_ERROR","message":"...","details":[]}
```

| 상태 | 의미 |
| --- | --- |
| 400 | 검증 실패 |
| 401 | 미인증 |
| 403 | 접근금지 |
| 404 | 없는 리소스 |
| 409 | 거래 버전 충돌 |

## 구현 전 확인할 계약
성공 상태와 본문 구조, 생성 필수 필드, PATCH 누락·null 처리, 페이지 시작값·기본/최대 크기·정렬 문법, q 검색 대상, from/to의 형식·포함 경계, 예산 미설정 응답, 집계 스키마, 오류 코드 목록·details 구조, 타인 리소스에 대한 403/404 구분, CSRF 토큰 획득·전달 방법은 원본에 상세히 명시되지 않았다. 임의로 확정하지 않고 질문 후 문서에 반영한다. CSV는 P2이며 원본에 경로가 없어 추가하지 않는다.

## 인증·사용자 설계 갱신 (2026-10-20)
PROJECT_SPEC의 사용자 승인에 따라 이메일은 애플리케이션에서 앞뒤 공백 제거·소문자 통일 후 저장한다. DB 컬럼은 VARCHAR(254)다. 이번 변경은 users V1 SQL이며 API 필수 필드·응답 등 미확정 계약을 추가 확정하거나 엔드포인트를 구현하지 않았다.

## 카테고리 설계 갱신 (2026-10-20)
PROJECT_SPEC의 사용자 승인에 따라 이름은 최대 50자, 앞뒤 공백 제거 후 빈 이름 금지·대소문자 구분으로 확정했다. type은 INCOME·EXPENSE이며 신규 archived는 false다. 같은 사용자·유형·이름은 보관 상태와 관계없이 중복 생성할 수 없다. DELETE는 보관 처리한다. 보관 카테고리의 신규 거래 선택·복원 정책 및 오류 응답의 상세 계약은 아직 미확정이다. V2 SQL만 작성했으며 카테고리 API·입력 정규화·소유권 검증은 미구현이다.

## 거래 계약 구체화 (2026-10-20 사용자 승인)
PROJECT_SPEC의 V3 설계에 따라 생성 시 type·amount·categoryId·occurredOn·description이 필요하다. description은 빈 내용 금지·최대 100자다. memo는 선택·최대 1000자이며 미입력 시 빈 문자열로 저장한다. 명시적 null과 PATCH 누락/null 처리 계약은 여전히 미확정이다.
amount는 1~999999999999999의 정수 원화로 BigDecimal 검증 후 저장한다. DB 반올림에 맡기지 않는다. user_id는 인증 사용자로 결정하며 카테고리 소유자·유형이 일치해야 한다. 보관 카테고리는 새 거래·카테고리 변경 시 선택을 거부하고, 기존 연결을 유지하는 금액·메모 수정은 허용한다. 이전 신규 선택 미확정 기록을 갱신하며 복원은 미확정으로 남긴다.
version은 0에서 시작하며 이후 JPA 낙관적 잠금으로 관리한다. 기존 409 충돌 요구사항은 유지하고 PATCH version 전달 계약은 추후 확정한다. 이번 변경은 DB SQL이며 API 입력 검증·인증·충돌 처리는 미구현이다.

## JPA 매핑 구현 범위 (2026-10-20)
User·Category·Transaction 엔티티와 @Version 매핑을 추가했으나 엔드포인트는 여전히 없다. JPA OptimisticLockException을 409 응답으로 변환하는 처리, API version 전달 계약, 인증·입력 검증·정규화·보관 정책은 미구현·미확정 상태를 기존 구분대로 유지한다. 엔티티는 API DTO가 아니며 passwordHash 등을 응답에 직접 노출하지 않는다.

## Repository 구현 범위 (2026-10-20)
카테고리·거래 Repository에 사용자 범위 상세·목록 조회와 저장을 추가했다. Controller·인증·서비스·API 응답은 추가하지 않았다. 내부 findAllByUserId는 보관 카테고리도 포함하는 기본 목록이며 API의 검색·정렬·페이지 계약을 대체하지 않는다. userId는 향후 서비스가 인증 정보에서 얻어 전달해야 한다. 타인 리소스에 대한 HTTP 403/404 및 오류 상세 계약은 미확정 상태를 유지한다.

## 인증 API 확정 (2026-10-20 사용자 승인)
PROJECT_SPEC의 인증 정책 확정 절과 [AUTH_DESIGN](AUTH_DESIGN.md)이 기존 인증 세부 계약 미확정 상태를 갱신한다. 요청은 JSON email·password이며 이메일 정규화·형식·254자, 비밀번호 필수·12~128자를 검사한다. 가입 201·자동 로그인 없음, 로그인 200, me 200은 모두 id·email만 반환한다. 로그아웃은 204다. 인증된 상태의 재가입·재로그인은 409 ALREADY_AUTHENTICATED다.
GET /api/auth/csrf를 추가했다. 응답은 token·headerName이고 회원가입·로그인·로그아웃 POST에도 X-CSRF-TOKEN이 필요하다. 로그인·로그아웃 뒤 토큰을 재조회한다. 비인증 me는 401 UNAUTHENTICATED, 로그인 실패는 동일한 401 INVALID_CREDENTIALS, 중복 이메일은 409 EMAIL_ALREADY_EXISTS, CSRF 오류는 403 CSRF_INVALID다. 400 VALIDATION_ERROR/INVALID_JSON, 415 UNSUPPORTED_MEDIA_TYPE과 429 LOGIN_RATE_LIMITED(Retry-After)를 사용한다. 공통 details는 [{field,message}]이며 입력값을 포함하지 않는다. 이 절의 인증 409 구현은 거래 버전 충돌 409 구현을 의미하지 않는다.
로그인 제한·쿠키 정책은 PROJECT_SPEC을 따르며 사용자 ID는 세션 principal에서 제공한다. 실제 실행 방법은 [AUTH_GUIDE](AUTH_GUIDE.md)를 따른다. 거래 엔드포인트·API 페이지/검색·PATCH 계약은 이번 범위가 아니다.

## 카테고리 API 확정 및 구현 (2026-10-20 사용자 승인)
PROJECT_SPEC의 카테고리 서비스·API 계약과 [CATEGORY_API_DESIGN](CATEGORY_API_DESIGN.md)이 기존 카테고리 응답·PATCH·타인 오류 미확정 기록을 갱신한다.
- GET /api/categories?includeArchived=false는 사용 중인 목록, true는 보관 포함. 200 DTO 배열이며 name·id 오름차순이다. 페이지/검색 계약은 추가하지 않는다.
- POST는 name·type 필수, 201 DTO. PATCH는 name만 필수, 200 DTO. name은 문자열 strip·빈 이름 금지·최대 50자, type은 문자열 INCOME/EXPENSE다. DTO는 id·name·type·archived·createdAt이다. user_id는 세션에서 결정한다.
- POST의 name/type 외 필드와 PATCH의 name 외 필드, 누락/null/빈 객체/잘못된 타입은 400이다. 유형은 생성 후 변경하지 않는다.
- DELETE는 보관 처리, 반복 요청도 204다. 보관 항목 이름 수정은 허용한다. 중복은 보관 여부와 관계없이 409 CATEGORY_ALREADY_EXISTS, 없거나 타인 소유인 PATCH/DELETE는 404 CATEGORY_NOT_FOUND다.
- 미인증 401·CSRF 403을 유지한다. UUID/includeArchived 변환 오류는 공통 400 VALIDATION_ERROR로 처리한다. [CATEGORY_API_GUIDE](CATEGORY_API_GUIDE.md)에 전체 테스트·실제 HTTP 명령을 기록했다.

## 이름순 정렬 변경 근거 (2026-10-20)
사용자 요청으로 생성순 대신 name ASC·id ASC를 적용했다. 현재 로컬 DB en_US.utf8에서 한글 가나다순을 확인했다. API 기본 목록과 includeArchived=true에 모두 적용하며 응답·필터 계약은 유지한다. 배포 DB의 collation은 별도로 검증한다.

## 거래 등록·상세 API 구체화 (2026-10-21)
[PROJECT_SPEC](PROJECT_SPEC.md)의 거래 등록·상세 절과 [TRANSACTION_API_DESIGN](TRANSACTION_API_DESIGN.md)을 따른다. 이전의 생성 메모 null 처리 미확정 기록을 갱신하며 PATCH 누락/null 계약은 아직 미확정이다.
- POST /api/transactions: type·amount·categoryId·occurredOn·description 필수, memo 선택. 누락과 null은 모두 ''로 저장한다. 성공 201.
- GET /api/transactions/{id}: 인증 사용자 소유 거래 상세, 성공 200. 공통 응답은 id·type·amount·categoryId·occurredOn·description·memo·version이다. version 초기값은 0이다.
- amount는 JSON 정수 숫자(1~999999999999999)만 허용하고 문자열·소수·지수 표기를 거부한다. type은 문자열 INCOME/EXPENSE, 날짜는 실제 YYYY-MM-DD(0001~9999), UUID는 표준 형식이다. description은 strip·빈 내용 금지·100자, memo는 문자열 공백 유지·1000자다.
- 미지원 필드·사용자/ID/version/시각 위조는 400. 미인증 401·CSRF 403. 없는/타인 거래 404 TRANSACTION_NOT_FOUND, 없는/타인 카테고리 404 CATEGORY_NOT_FOUND, 유형 불일치 400 CATEGORY_TYPE_MISMATCH, 보관 카테고리 400 CATEGORY_ARCHIVED다. 입력 오류는 기존 VALIDATION_ERROR/INVALID_JSON 공통 형식이며 실제 입력값은 반환하지 않는다.
- 보관 이후 기존 거래 상세는 허용한다. 목록·수정·삭제·버전 충돌 HTTP 409는 이번 범위가 아니다. [실행 가이드](TRANSACTION_API_GUIDE.md)에 전체 테스트와 수동 HTTP 명령을 기록했다.


## 거래 목록·수정·삭제 확정 (2026-10-21)
사용자가 남은 거래 API 설계를 승인해 조회·부분 수정·삭제 계약을 확정했다. 앞선 미구현/미확정 기록에서 해당 항목을 갱신한다.
- GET /api/transactions: month(YYYY-MM, 0001~9999)·type(INCOME/EXPENSE)·categoryId(UUID)·q를 AND로 적용한다. month 누락은 전체 기간, 유효하지만 없는/타인 카테고리 필터는 빈 목록이다. q는 strip·최대 100자이며 description 또는 memo의 대소문자 무시 부분 검색이다. %, _, 역슬래시는 실제 문자로 검색한다.
- page 기본 0, size 기본 20(1~100). sort 기본 occurredOn,desc이며 occurredOn/amount/createdAt과 asc/desc만 허용한다. id도 같은 방향으로 정렬해 동률을 고정한다. 음수 페이지·미지원 조건과 JPA 조회 오프셋(page*size)이 정수 범위를 넘는 요청은 400이다.
- 목록 응답은 content·page·size·totalElements·totalPages. 조회 결과가 없으면 content=[]와 합계 0, 마지막 페이지를 넘으면 content=[]와 실제 전체 합계를 반환한다.
- PATCH /api/transactions/{id}: 비음수 정수 version 필수, type·amount·categoryId·occurredOn·description·memo 중 최소 1개를 전달한다. 누락은 유지, memo:null은 빈 문자열, 나머지 null과 미지원 필드는 400. 값 검증은 등록과 같다.
- 변경 후 사용자·유형이 일치해야 한다. 다른 카테고리를 선택하면 사용 중인 항목이어야 한다. 기존 카테고리 ID·유형을 유지하는 수정은 보관 후에도 허용한다.
- 현재와 다른 version 또는 실제 동시 수정/삭제 충돌은 409 TRANSACTION_VERSION_CONFLICT. 실제 수정은 version 증가, 동일 값 요청은 현재 version 유지다. 오래된 version은 동일 값이어도 409다.
- DELETE /api/transactions/{id}: 소유 거래 물리 삭제 204. version/본문 불필요. 타인·없는 거래·반복 삭제는 404 TRANSACTION_NOT_FOUND. 카테고리는 유지한다. 수정/삭제는 기존 세션·CSRF 정책을 따른다.
- 스키마·의존성·프레임워크 버전은 유지한다. 자세한 계약과 실행은 [설계](TRANSACTION_API_DESIGN.md)와 [가이드](TRANSACTION_API_GUIDE.md)를 따른다.


## 프런트엔드 초기 구성 반영 (2026-10-21)
새 엔드포인트 없이 /api/me·/api/auth/csrf·로그인/로그아웃·거래 목록·보관 포함 카테고리를 브라우저에서 사용한다. API 상대 경로와 쿠키를 유지하고 쓰기 요청마다 새 CSRF 헤더를 전달한다. 목록의 UI 기본값은 전체 기간·20건·최신 날짜순이며 URL 조건으로 조회한다. 클라이언트 owner 조건은 전송하지 않는다. 401 세션 만료는 로그인으로 이동하고 조회 캐시를 비운다. 상세 서버 계약은 변경하지 않는다. 기준은 PROJECT_SPEC의 최신 프런트엔드 절이며 실행은 [FRONTEND_GUIDE](FRONTEND_GUIDE.md)를 따른다.
