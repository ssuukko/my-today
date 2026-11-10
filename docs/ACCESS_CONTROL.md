# 관리자와 일반 사용자

사용자 요청에 따라 앱 권한을 `ADMIN`과 `USER`로 구분한다. PostgreSQL 접속 계정 `mytoday_app`과 앱 로그인 계정은 서로 다른 개념이다.

| 동작 | USER | ADMIN |
|---|---|---|
| 본인 프로필·일정·할 일 관리 | 가능 | 가능 |
| 다른 회원의 개인 일정·할 일·금융 기록 열람 | 불가 | 불가 |
| 회원 기본 정보 목록 | 불가 | 가능 |
| 가입 초대 코드 확인·복사 | 불가 | 가능 |
| 가입 시 관리자 권한 선택 | 불가 | 불가 |

회원가입은 항상 USER다. 기존 계정도 Flyway V3 적용 시 USER가 되며 자동 승격하지 않는다. 관리자 API는 각 요청에서 현재 DB 역할을 다시 검사하므로 오래된 세션에 ADMIN 권한이 남아 있다고 허용하지 않는다. 비로그인은 401, 일반 사용자는 403이다.

로그인 화면은 공통이다. 최초 설정 후 관리자는 `/admin`, 일반 사용자는 `/today`로 이동한다. 정상적인 내부 복귀 경로는 보존한다. 회원 목록에는 이메일·표시 이름·역할·가입일·초기 설정 여부만 제공하고 비밀번호 해시나 개인 기록은 제공하지 않는다.

## 최초 관리자

최초 관리자만 운영자가 별도로 초기화한다. 공용 기본 비밀번호·공개 초기화 API·첫 가입자 자동 승격은 제공하지 않는다.

현재 Supabase 개발 환경에는 `admin@mytoday.local`을 ADMIN으로 생성했다. 무작위 초기 비밀번호는 현재 PC의 `.env.admin`에만 저장되어 있다. 이 ID는 메일 검증이 없는 개인 베타용 로그인 ID이며 공용 계정이 아니다. 브라우저 검증 과정에서 기본 설정까지 완료했으므로 바로 관리자 화면으로 진입할 수 있다.

- `.env.admin`: 최초 관리자 이메일과 무작위 초기 비밀번호. 파일 권한 0600, Git·Docker 빌드 컨텍스트 제외.
- `.env.admin.example`: 비밀 값 없는 양식. 다른 환경에서는 복사한 뒤 값을 설정한다.
- 두 값이 모두 없으면 초기화는 실행하지 않는다. 일부만 있거나 이메일·비밀번호 정책에 맞지 않으면 실패한다.
- 초기화는 트랜잭션 잠금으로 동시에 두 관리자를 만들지 않도록 한다.
- 이미 ADMIN이 있으면 계정·비밀번호를 변경하지 않는다. 같은 이메일의 USER를 ADMIN으로 올리지 않는다.
- 초기 관리자도 첫 로그인 때 기본 설정을 완료한다.

Supabase에서 최초 관리자를 구성할 때는 먼저 새 이미지를 빌드한다.

```bash
docker compose --env-file .env --env-file .env.supabase \
  -f infra/compose.local.yml -f infra/compose.supabase.yml build api web
```

별도 컨테이너에만 초기화 비밀 값을 전달한다. API 포트는 호스트에 공개하지 않는다.

```bash
docker compose --env-file .env --env-file .env.supabase --env-file .env.admin \
  -f infra/compose.local.yml -f infra/compose.supabase.yml \
  -f infra/compose.admin-bootstrap.yml \
  run -d --rm --no-deps --name mytoday-admin-bootstrap api
```

초기화 성공과 관리자 생성 여부를 확인한 후 이 컨테이너를 종료한다.

```bash
docker stop mytoday-admin-bootstrap
```

정상 앱은 초기화 설정을 제외하고 실행한다. `.env.admin`을 일반 서버 환경이나 프런트엔드에 전달하지 않는다.

```bash
docker compose --env-file .env --env-file .env.supabase \
  -f infra/compose.local.yml -f infra/compose.supabase.yml \
  up -d --no-build --wait api web
```

기본 `scripts/dev-up.sh`는 로컬 DB용이다. 위 초기화는 명시한 Supabase DB에만 적용하며 기존 로컬 계정은 변경하지 않는다.

## 현재 범위

관리자 화면은 회원 목록과 초대 확인만 제공한다. 관리자 권한 부여·회수, 계정 삭제·정지, 비밀번호 재설정 UI는 추가하지 않았다. 권한 해제 후의 세션 차단은 API 통합 테스트로 검증한다. 기존 공개 가입·이메일 복구 제한도 유지한다.

실제 배포·최초 관리자 생성·검증 결과는 [검증 기록](VALIDATION.md)에 기록한다. 초기 비밀번호는 코드·문서·로그에 쓰지 않는다.

현재 서버에서는 일회성 초기화 컨테이너를 제거했고 일반 API 환경에 초기화 비밀 값이 없는 것을 확인했다. Supabase에는 ADMIN 1명만 남겼고 검증용 USER 계정과 기록은 정리했다.
