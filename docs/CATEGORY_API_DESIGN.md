# 카테고리 서비스·API 설계

작성일: 2026-10-20. 요구사항 기준: [PROJECT_SPEC](PROJECT_SPEC.md).
상태: 2026-10-20 사용자 승인으로 아래 세부 계약을 확정하고 PROJECT_SPEC에 반영했다. 코드·테스트 작성 후 임시 PostgreSQL 전체 32건과 임시 서버 실제 HTTP 검증을 완료했다. 기존 사용자 DB·서버는 유지했다.

## 구현 범위
기존 네 엔드포인트를 인증 SessionUser의 UUID와 연결한다. Controller·DTO·Service·Repository 보완·엔티티 변경 메서드·입력 검증·공통 오류·테스트를 포함한다. 거래 API·프런트엔드·카테고리 복원은 제외한다. DB 스키마와 기존 마이그레이션은 유지한다.

## API 계약
| 경로 | 입력 | 성공 |
| --- | --- | --- |
| GET /api/categories | includeArchived=false 기본, true면 보관 포함 | 200, 카테고리 DTO 배열, 없으면 [] |
| POST /api/categories | JSON name·type 필수 | 201, 생성 DTO |
| PATCH /api/categories/{id} | JSON name 필수, 이름만 수정 | 200, 수정 DTO |
| DELETE /api/categories/{id} | 본문 없음 | 204, archived=true, 반복 요청도 204 |

DTO 필드: id·name·type·archived·createdAt. user_id는 요청에서 받지 않고 응답에서도 제외한다. 목록은 name 오름차순 후 id 오름차순이며 사용자 범위 조건을 DB 쿼리에 포함한다. 이번에는 페이지·검색 계약을 도입하지 않는다.

## 검증·동작
- name은 기존 승인대로 strip 후 필수·빈 이름 금지·최대 50자·대소문자 구분이다. type은 INCOME·EXPENSE다.
- 생성 시 archived=false. type은 생성 후 변경할 수 없게 한다. PATCH는 name만 허용하고, 누락/null/빈 객체·지원하지 않는 필드는 400으로 처리한다. POST도 name·type 외 필드는 거부한다.
- 보관된 카테고리의 이름 변경은 허용하며 복원 API는 추가하지 않는다. 기존 보관 카테고리 신규 거래 선택 금지 정책은 거래 서비스 구현 때 연결한다.
- 동일 사용자·유형·정규화 이름 중복은 보관 여부와 관계없이 409 CATEGORY_ALREADY_EXISTS다. 사전 검사와 DB UNIQUE(uk_categories_user_type_name) flush 오류를 함께 처리한다. 다른 사용자·다른 유형의 동일 이름은 허용한다.
- 없거나 타인 소유인 카테고리의 PATCH/DELETE는 동일한 404 CATEGORY_NOT_FOUND다. UUID 형식 오류·입력 오류는 400, 미인증은 401, CSRF 오류는 403이다. 기존 보안 필터의 CSRF 우선 검사 순서는 유지한다.
- 소유자는 Controller가 인증 principal에서 얻어 서비스에 전달한다. 클라이언트 userId/user_id를 신뢰하지 않는다. 쓰기 메서드는 서비스 트랜잭션으로 묶는다. 물리 삭제 메서드는 추가하지 않는다.

## 검증 계획
HTTP 보안·입력·오류 계약은 MockMvc, DB 저장·중복·이름 수정·보관·반복 보관·정렬·사용자 격리는 실제 PostgreSQL 통합 테스트로 검증한다. 두 사용자 세션으로 타인 ID 수정/보관과 user_id 위조가 차단되는지 확인하고 기존 20개 테스트도 회귀 확인한다. PROJECT_SPEC과 관련 문서에 확정 정책을 반영했다. 실행 가이드는 CATEGORY_API_GUIDE를 따른다. 커밋·푸시는 사용자가 진행한다.

## 이름순 정렬 갱신 (2026-10-20)
사용자 요청으로 생성 시각 대신 name ASC·id ASC를 사용한다. 임시 PostgreSQL의 관련 16개 테스트로 가나다순·동명 ID 순서·보관 필터·사용자 격리를 검증했다. 스키마·collation은 변경하지 않았고 기존 환경에서 새 코드를 확인하려면 서버를 재시작한다.
