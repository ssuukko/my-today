# Supabase PostgreSQL 연결

2026-11-08 · **연결 완료.** `http://localhost:8088`에서 실행 중인 API가 Supabase PostgreSQL을 사용한다. MCP로 프로젝트·스키마·역할·마이그레이션을 재확인했다. 기존 로컬 DB는 원본 그대로 보존했으며 계정·기록을 자동 이전하지 않았다.

| 항목 | 확인 결과 |
|---|---|
| 프로젝트 | `my-today` · `hxckrcnpgoulcxijgrll` |
| 리전 | 서울 · `ap-northeast-2` |
| 상태 | `ACTIVE_HEALTHY` |
| 생성 비용 조회·확인 | 월 0원, 무료 플랜 범위 |
| PostgreSQL | 17.11 |
| 접속 | `aws-0-ap-northeast-2.pooler.supabase.com:5432` · Session pooler |
| 앱 계정 | `mytoday_app.hxckrcnpgoulcxijgrll` |
| 앱 스키마 | `mytoday` · Flyway V1–V7 적용 완료 |

[프로젝트 대시보드](https://supabase.com/dashboard/project/hxckrcnpgoulcxijgrll). 기존 다른 프로젝트는 변경하지 않았다. 무료 리소스의 사용량·정책 변경으로 영구 0원을 보장한다는 뜻은 아니다.

## 구성

Supabase는 PostgreSQL 저장소로 사용하고 기존 Spring의 세션 인증·CSRF·사용자 격리와 Flyway를 유지한다. 브라우저가 Supabase에 직접 접속하지 않으므로 Supabase JS SDK·Supabase Auth·anon/service_role 키가 필요하지 않다. 외부 DB는 별도 Compose 파일을 명시해 선택한다. 현재 Docker API는 이 외부 DB 설정으로 실행 중이다.

`mytoday_app`은 로그인과 앱 스키마 사용·테이블 생성 권한만 가진다. 슈퍼유저·DB 생성·역할 생성·권한 상속·복제·RLS 우회 권한은 없다. 앱 테이블과 Flyway 이력의 소유자는 이 역할이다. `anon`과 `authenticated`에는 스키마 접근 권한이 없다.

무료 플랜은 현재 DB 500MB, egress 5GB, 활성 프로젝트 2개이며 1주 저활동 시 일시정지될 수 있다. 영구 무료 상시 가동이나 자동 백업을 보장하는 구성은 아니다. [공식 요금](https://supabase.com/pricing)

## 연결 전 확인

1. Supabase 설치·계정 연결 후 MCP로 조직·프로젝트를 조회하고, 이 앱 전용의 무료 프로젝트를 선택한다. 기존 프로젝트에 임의로 테이블을 만들지 않는다.
2. 프로젝트의 **Session pooler, 포트 5432** 주소와 사용자명을 사용한다. IPv4에서도 사용할 수 있으며 현재 JDBC·Flyway에는 Transaction pooler 6543을 사용하지 않는다. [연결 방식](https://supabase.com/docs/guides/database/connecting-to-postgres)
3. DB 비밀번호는 서버 비밀 값으로만 저장한다. 채팅·Git·프런트 `VITE_*` 변수나 JDBC URL의 비밀번호 부분에 넣지 않는다. 계정 연결 권한과 DB 접속 비밀번호는 별개다.
4. Flyway와 JDBC의 기본 스키마를 모두 `mytoday`로 맞춘다. 최초 연결 역할에는 이 스키마를 생성할 권한이 필요하다. 전용 역할을 사용하면 관리자가 미리 스키마를 생성하고 소유권을 줄 수 있다.
5. Supabase Data API의 노출 스키마에 `mytoday`를 추가하지 않는다. JDBC 전용 프로젝트에서는 Data API를 끌 수 있다. SQL로 만든 테이블에 RLS가 자동 적용된다고 가정하지 않는다. [API 보안](https://supabase.com/docs/guides/api/securing-your-api)

## 실행 설정

현재 `.env.supabase`에 검증된 주소·앱 계정·무작위 비밀번호가 저장되어 있으며 파일 권한은 0600, Git에서는 제외된다. 기존 파일을 예시로 덮어쓰지 않는다. 새 환경을 준비할 때만 `.env.supabase.example`을 복사한다. 기존 `.env`는 로컬 DB 원본과 초대 가입 설정을 유지한다.

JDBC URL은 다음 형태다. 호스트와 사용자명은 Supabase의 Connect 화면에서 확인하며 예시 값을 그대로 사용하지 않는다.

```text
EXTERNAL_DB_URL=jdbc:postgresql://SESSION_POOLER_HOST:5432/postgres?sslmode=verify-full&sslrootcert=/run/certs/supabase-ca.crt
EXTERNAL_DB_USERNAME=mytoday_app.PROJECT_REF
EXTERNAL_DB_PASSWORD=<서버 전용 비밀 값>
```

`verify-full`은 인증서와 호스트를 검증한다. 실제 풀러는 Java 기본 신뢰 저장소에 없는 Supabase CA를 사용하므로, 공식 공개 CA인 `infra/certs/supabase-ca.crt`를 `/run/certs/supabase-ca.crt`에 읽기 전용으로 마운트했다. `sslfactory`를 지정하지 않아 기본 LibPQFactory가 이 파일을 사용한다. TLS 검증은 끄지 않았다. [pgJDBC SSL](https://jdbc.postgresql.org/documentation/ssl/)

CA는 [공식 Dashboard 설정](https://github.com/supabase/supabase/blob/master/apps/studio/hooks/custom-content/custom-content.json)이 지정한 [공개 다운로드](https://supabase-downloads.s3-ap-southeast-1.amazonaws.com/prod/ssl/prod-ca-2021.crt)에서 받았다. 인증서는 비밀 키가 아니며 만료일은 2031-04-26, SHA-256은 `807025AD50D4ED219D2C9C7D299C004F824EB00CF7F65AFEF607D07B72E6CAFA`다. 공급자가 CA를 교체하거나 만료되기 전에 공식 배포본을 다시 확인한다. [Supabase SSL 안내](https://supabase.com/docs/guides/platform/ssl-enforcement)

Supabase 연결로 다시 실행하는 명령은 다음과 같다. Flyway는 이미 적용된 V1–V7을 검증하고 이후의 새 마이그레이션만 적용한다.

```bash
docker compose --env-file .env --env-file .env.supabase \
  -f infra/compose.local.yml -f infra/compose.supabase.yml \
  up -d --build --wait api web
```

로컬 DB 컨테이너를 새로 시작할 필요가 없으며 기존 컨테이너·볼륨은 삭제하지 않는다. 검증용 클라우드 USER 계정과 기록은 정리하고 초기 ADMIN 1명만 유지했다. 로그인 ID·권한과 초기화 절차는 [권한 문서](ACCESS_CONTROL.md)를 따른다. 기존 로컬 계정 1개는 원본 DB에 그대로 있으며 클라우드에 복사하지 않았다. 따라서 기존 로컬 계정으로 클라우드에 로그인할 수 있다고 가정하지 않는다. 데이터 이전은 별도로 검증해야 한다.

로컬 DB로 복귀할 때는 원래 실행 스크립트를 사용한다. Supabase 프로젝트와 데이터는 삭제되지 않는다.

```bash
./scripts/dev-up.sh
```

## MCP와 배포의 범위

현재 필요한 연결은 **Supabase**다. MCP는 프로젝트·설정 관리에 사용하며 앱 실행 중의 DB 연결은 JDBC가 담당한다. 이번에는 기존 관리자 암호를 읽거나 초기화하지 않고 앱 전용 계정과 비밀번호를 새로 구성했다.

풀러 주소는 공식 관리 API의 설정으로 확인했다. 인증 토큰은 저장소나 명령 인수에 넣지 않았다. 2026-11-10 MCP의 OAuth 갱신 오류로 직접 SQL 재조회는 하지 못했으며, 실행 중인 API의 Flyway 로그에서 V4–V6 적용 성공을 확인했다.

Vercel은 이 DB 연결에 필수가 아니다. Vercel의 DB는 Marketplace의 Supabase·Neon 등 외부 공급자를 사용한다. [Vercel 데이터베이스](https://vercel.com/docs/postgres)

추후 Vercel 웹·컨테이너 배포까지 진행하면 Vercel 연결을 추가할 수 있다. 현재 Spring 세션·로그인 속도 제한은 단일 프로세스 메모리이므로 자동 확장·축소하는 배포에서는 지속 세션과 공유 제한 저장소를 먼저 검토해야 한다. Vercel Container는 현재 모든 플랜의 Beta이며 자동 축소 동작이 있다. [Container 런타임](https://vercel.com/docs/functions/runtimes/container)

## 검증 상태

- MCP 등록·OAuth 인증·조직/프로젝트 조회·조직 Free 플랜 확인: 성공.
- 원격 전용 프로젝트: 무료 플랜·서울 리전 `ACTIVE_HEALTHY` 확인.
- 앱 계정·스키마: 최소 권한과 익명 접근 차단을 MCP로 확인. 관리자 암호는 변경하지 않음.
- 실제 TLS·JDBC·Flyway: 공식 CA와 호스트 검증을 적용해 연결 성공, V1–V7 성공 확인.
- 실제 Chrome E2E: 가입·로그인·설정·로그아웃과 오늘 할 일 완료·내일 일정 저장 2개 시나리오 통과.
- Docker 앱: 8088에서 생성한 기록을 Supabase에서 직접 조회해 저장 위치 확인. API·웹 모두 healthy.
- 권한·테스트 정리: 실제 ADMIN/USER 경계와 개인 기록 격리 확인. 테스트 USER 2명과 그 기록 삭제, 운영 ADMIN 1명·USER 0명·할 일 0건·일정 0건 확인.
- 원본 보존: 로컬 사용자 1명·할 일 0건·일정 0건 유지. 기존 데이터 이전·공개 웹 배포는 수행하지 않음.
- 구성·비밀 값: Compose 병합·공식 CA 지문·읽기 전용 마운트·0600 권한·Git 제외 확인 통과. [검증 기록](VALIDATION.md)
