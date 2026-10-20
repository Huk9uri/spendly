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
