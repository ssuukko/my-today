# Phase 0–1 API

기본 경로 `/api/v1`. 동일 출처의 서버 세션 쿠키 `MT_SESSION` 사용. 응답에 `Cache-Control: no-store`, `X-Request-ID`를 제공합니다. 오류는 `application/problem+json`의 `status`, `code`, `message`를 확인합니다. 개발 OpenAPI는 API 내부 `/v3/api-docs`, `/swagger-ui/index.html`입니다(외부 Nginx에는 노출하지 않음).

| 메서드·경로 | 요청/응답 |
|---|---|
| GET `/auth/csrf` | `{token,headerName}`. 모든 변경 요청 전에 발급받아 지정된 헤더에 전송 |
| POST `/auth/signup` | `{email,password,passwordConfirmation,displayName,inviteToken}` → 201 `{id}` |
| POST `/auth/login` | `{email,password}` → 세션 응답 |
| POST `/auth/guest` | 빈 요청, CSRF 필수 → 독립 임시 USER 계정과 세션 응답 |
| GET `/auth/session` | 인증 시 세션 응답, 비인증 401 |
| POST `/auth/logout` | CSRF 필수, 세션 무효화·쿠키 만료, 204 |
| GET `/users/me` | 세션 응답, 인증 필수 |
| PATCH `/users/me/settings` | `{displayName,timezone,baseCurrency}` → 세션 응답, 최초 설정 완료 처리 |

세션 응답: `{user:{id,email,displayName,timezone,baseCurrency,role},onboardingCompleted}`. `role`은 `USER` 또는 `ADMIN`입니다. 회원가입은 항상 USER이며 요청의 역할 값으로 승격할 수 없습니다. 시간대는 IANA 이름, 통화는 ISO 4217 코드입니다. 비밀번호는 12자 이상·UTF-8 72바이트 이하이며 BCrypt 해시로만 저장합니다. 초대 코드 미설정 또는 불일치 시 가입이 차단됩니다. CSRF 토큰은 로그인 이후 다시 발급받습니다.

게스트 응답에는 `user.guest=true`가 추가됩니다. 일반 계정은 false입니다. 게스트는 온보딩 없이 입장하며 24시간 뒤 모든 개인 API 접근이 만료되고 기록은 주기적으로 정리됩니다.

## 관리자 API

`/admin/**`는 현재 DB 역할이 ADMIN인 경우에만 접근합니다. 익명은 401, 일반 사용자·권한이 해제된 기존 관리자 세션은 403입니다. ADMIN도 다른 회원의 개인 기록에는 접근할 수 없습니다.

| 메서드·경로 | 응답 |
|---|---|
| GET `/admin/users?size=100&cursor=` | `{items:[{id,email,displayName,role,onboardingCompleted,createdAt}],nextCursor,total}` |
| GET `/admin/invitation` | `{enabled,inviteToken}`. 비활성 상태의 토큰은 null |

회원 목록 크기는 1–100, 커서는 생성일·ID 순의 오프셋입니다. 비밀번호·해시·개인 기록은 포함하지 않으며 관리자 응답도 `no-store`입니다. 관리자 생성·승격 API는 제공하지 않습니다. [초기화와 권한 정책](ACCESS_CONTROL.md)

## 할 일·일정·오늘

모든 경로는 인증이 필요하며 생성·수정·완료·삭제에는 CSRF 헤더가 필요합니다. 소유자는 세션에서 결정합니다. 다른 사용자 소유 리소스와 삭제한 리소스는 404입니다.

| 메서드·경로 | 요청/응답 |
|---|---|
| GET `/tasks?view=all&status=&priority=&date=&size=100&cursor=` | `{items,nextCursor}` |
| GET `/tasks/{id}` | 할 일 한 건 |
| POST `/tasks` | 할 일 입력 → 201 할 일 |
| PATCH `/tasks/{id}` | 전체 편집 필드와 `version` → 할 일 |
| POST `/tasks/{id}/complete` | `{version}` → 완료된 할 일 |
| POST `/tasks/{id}/reopen` | `{version}` → TODO 할 일 |
| DELETE `/tasks/{id}?version=0` | 소프트 삭제 → 204 |
| GET `/calendar/events?from=2026-11-01&to=2026-12-01&size=100&cursor=` | 범위에 겹치는 일정 `{items,nextCursor}` |
| GET `/calendar/events/{id}` | 일정 한 건 |
| POST `/calendar/events` | 일정 입력 → 201 일정 |
| PATCH `/calendar/events/{id}` | 전체 편집 필드와 `version` → 일정 |
| DELETE `/calendar/events/{id}?version=0` | 소프트 삭제 → 204 |
| GET `/dashboard/today?date=2026-11-07` | `{date,timezone,events,tasks,taskCount,completedTaskCount,eventCount}` |

할 일 응답은 `{id,title,status,priority,dueDate,dueAt,estimatedMinutes,completedAt,version}`입니다. 입력은 `id`, `completedAt`을 제외한 편집 필드를 사용합니다. 생성 기본값은 `status=TODO`, `priority=P2`이며 수정은 전체 필드와 현재 `version`을 전송합니다. `status`는 `TODO/IN_PROGRESS/DONE/CANCELED`, `priority`는 `P0/P1/P2/P3`, 제목은 1–200자, 예상 시간은 선택이며 1–10080분입니다. `dueDate`는 날짜, `dueAt`은 오프셋을 포함한 ISO 시각이며 동시에 지정하지 않습니다. 마감 없는 할 일은 두 필드를 null로 보냅니다.

할 일 `view`는 `all/today/upcoming/overdue/completed`입니다. `date`의 기본 기준일은 프로필 시간대의 오늘입니다. `all`에 날짜를 지정하면 그 날짜 마감만 조회합니다. 예정은 기준일 이후, 밀린 일은 기준일 이전의 미완료·미취소 기록입니다. 시각 마감도 사용자 시간대로 날짜를 판정합니다.

일정 응답·입력은 `{id,title,allDay,startAt,endAt,startDate,endDateExclusive,timezone,location,notes,version}`입니다. 생성 시 `id`, `version`은 생략합니다. 종일 일정은 날짜 필드만 지정하며 시작일 포함·종료일 제외입니다. 예를 들어 11월 8일 하루는 `startDate=2026-11-08`, `endDateExclusive=2026-11-09`입니다. 시간 지정 일정은 날짜 필드를 null로 두고 오프셋이 있는 `startAt/endAt`을 지정합니다. 종료는 시작보다 뒤여야 하며 `timezone`은 유효한 시간대입니다. 저장된 절대 시각과 일정의 원래 시간대를 함께 보존합니다.

일정 조회는 `from` 포함·`to` 제외, 최대 366일이며 시각 경계는 프로필 시간대의 자정입니다. 오늘 집계도 해당 시간대의 자정부터 다음 자정까지 계산하므로 DST 전환일이 항상 24시간이라는 가정을 하지 않습니다. 종일 일정은 날짜 범위로 비교합니다. 홈 목록은 각 100건까지이며 전체 개수는 별도 집계합니다.

수정·삭제는 버전이 오래되면 409이며 덮어쓰지 않습니다. 완료·완료 취소는 바로 직전 버전으로 같은 상태를 재요청해도 버전을 늘리거나 활동을 생성하지 않습니다. 삭제 기록은 DB에 보존하지만 현재 복원 화면은 제공하지 않습니다.

목록 `size`는 1–100, `nextCursor=null`이면 마지막입니다. 커서는 현재 오프셋을 문자열로 나타내며 안정적인 생성시각·ID 순으로 조회합니다. 페이지를 넘기는 중 다른 기기에서 삭제하면 위치가 바뀔 수 있으므로 새로고침으로 목록을 다시 조회합니다. 장시간 목록 탐색의 일관성이 필요해지면 keyset 커서로 전환합니다.
