# 인증 설계

작성일: 2026-10-20. 기준: [PROJECT_SPEC](PROJECT_SPEC.md).
상태: 2026-10-20 사용자 승인으로 아래 세부 계약을 확정하고 PROJECT_SPEC에도 반영했다. 인증 코드와 테스트를 작성했고 사용자 실행의 로컬 XML로 전체 20개 통과·local 서버 기동을 확인했다. 사용자 실제 HTTP 가입·로그인·토큰 재조회·me·로그아웃 후 401 흐름도 확인해 로컬 검증을 완료했다. 운영 HTTPS·만료·프록시 검증은 배포 단계에 남아 있다.

## 목적과 구현 범위
회원가입·로그인·로그아웃·현재 사용자 조회를 Spring Security 세션 인증으로 구현한다. 인증 ID를 이후 카테고리·거래 서비스에 전달한다. 인증 우선 구현은 사용자 승인된 순서 변경이다. UserRepository·요청/응답 DTO·인증 서비스·보안 설정·공통 JSON 오류·로그인 속도 제한·테스트를 포함하고 거래 API는 제외한다.

## API 계약
| 메서드·경로 | 입력 | 성공 응답 |
| --- | --- | --- |
| GET /api/auth/csrf | 없음 | 200, token·headerName 반환 |
| POST /api/auth/register | JSON email·password | 201, id·email. 자동 로그인하지 않음 |
| POST /api/auth/login | JSON email·password | 200, id·email 및 세션 쿠키 |
| POST /api/auth/logout | 본문 없음, CSRF 필요 | 204, 세션 무효화·쿠키 제거 |
| GET /api/me | 세션 쿠키 | 200, id·email |

회원가입·로그인도 CSRF 검사를 수행한다. 비인증 CSRF 토큰 조회를 허용한다. 비인증 로그아웃은 올바른 CSRF가 있으면 204로 멱등 처리한다. 인증된 사용자의 로그인/가입 재요청은 세션 전환 없이 409로 거부한다.

## 입력·비밀번호
- 이메일: 기존 승인대로 앞뒤 공백 제거·Locale.ROOT 소문자 변환, 필수·이메일 형식·최대 254자. 중복은 DB UNIQUE도 활용해 409로 처리한다.
- 비밀번호: 12~128자, 공백을 제거하거나 대소문자 변경하지 않는다. 조합 규칙은 강제하지 않는다. 등록과 로그인 모두 동일한 허용 범위를 검사한다.
- Spring Security PBKDF2-SHA256(Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8)을 DelegatingPasswordEncoder 형식 식별자와 함께 저장한다. 평문·해시를 응답과 로그에 포함하지 않는다.
- 인증 오류는 계정 없음과 비밀번호 불일치 모두 동일한 401 메시지다. 없는 계정에도 더미 해시 검증을 수행한다.

## 세션·CSRF·출처
- 세션은 서버 HttpSession. 쿠키 HttpOnly, SameSite=Lax, 비활성 만료 30분, 운영 HTTPS에서 Secure=true. 로컬 HTTP에서만 Secure=false.
- Spring Security 표준 인증 흐름으로 세션 고정 공격 방지를 적용하고 SecurityContext를 세션에 저장한다. 자체 토큰·JWT·remember-me는 도입하지 않는다.
- CSRF 토큰은 세션에 보관하고 GET /api/auth/csrf JSON으로 가져와 X-CSRF-TOKEN 헤더로 전송한다. 로그인·로그아웃 이후 이전 토큰은 버리고 재조회한다.
- 초기 개발은 프런트 개발 서버 프록시로 동일 출처 요청을 보낸다. 외부 출처에 대한 credential CORS는 허용하지 않는다. 배포 시 동일 사이트/리버스 프록시와 운영 Secure 설정을 검증한다.

## 오류·로그인 속도 제한
- 공통 형식은 기존 code·message·details 배열. details는 필드명·메시지만 포함하고 입력값·인증값은 제외한다. 리다이렉트 대신 JSON을 반환한다.
- 400 입력/JSON 오류, 401 미인증·인증 실패, 403 CSRF 오류, 409 이메일 중복·인증 상태 충돌, 429 로그인 제한. version 충돌의 409와 구분되는 오류 코드를 사용한다.
- 로그인 시도 제한: IP별 5분 20회, IP+정규화 이메일별 5분 5회. 임계치를 넘으면 429·Retry-After를 반환한다. 요청 본문 검증을 통과한 인증 시도는 성공·실패 모두 집계한다.
- 단일 서버용 메모리 저장소에 만료 항목 제거·전체 항목 수 제한을 둔다. 재시작 시 초기화되며 다중 서버 지원은 범위 밖이다. X-Forwarded-For를 임의 신뢰하지 않고 초기에는 직접 연결 remoteAddr을 사용한다. 프록시 배포 시 신뢰 프록시 구성을 별도로 검증한다.

## 검증 계획
정상 가입·중복 가입·입력 실패·해시 저장·로그인 성공/실패·미인증 me·로그아웃 후 me·CSRF 누락/오류·로그인 후 토큰 갱신·세션 교체·로그인 제한/만료·민감값 비노출을 검사한다. 기존 8개 테스트 회귀 확인을 포함한다. 확정값을 기준·관련 문서에 반영했다. AuthTests 7개와 LoginRateLimiterTests 3개는 통과했으며 PostgreSQL 포함 전체 20개 테스트는 사용자 실행 결과로 통과를 확인했다. 실제 로컬 HTTP·운영 쿠키 검증은 별도로 수행한다. 실행 방법은 AUTH_GUIDE를 따른다.
