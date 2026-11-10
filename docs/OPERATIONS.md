# 실행과 운영

현재 로컬 Docker 앱은 Supabase 외부 DB를 사용합니다. 외부 DB 재실행 명령·인증서·검증 상태는 [클라우드 DB 문서](CLOUD_DATABASE.md)를 참고합니다. 아래 기본 명령은 원본 로컬 PostgreSQL로 실행하므로 사용할 DB를 명시적으로 선택합니다.

## 로컬

루트에서 `.env.example`을 `.env`로 복사하고 세 비밀 값(`POSTGRES_PASSWORD`, `DB_PASSWORD`, `REGISTRATION_INVITE_TOKEN`)을 채웁니다. `./scripts/dev-up.sh`는 DB·API·웹을 빌드하고 healthcheck를 기다립니다. `http://localhost:8088`에서 접속합니다. `./scripts/dev-down.sh`는 볼륨을 보존하고 종료합니다.

```bash
docker compose --env-file .env -f infra/compose.local.yml ps
docker compose --env-file .env -f infra/compose.local.yml logs --tail=100 api
```

DB 관리자는 별도 역할이며 API는 슈퍼유저가 아닌 `DB_USERNAME` 역할로 연결됩니다. DB는 호스트에 직접 공개하지 않습니다. API와 DB는 Docker 네트워크에서만 접근하고 웹만 localhost에 공개합니다.

초기화 스크립트는 새 볼륨에서 한 번 실행됩니다. 기존 볼륨에서 `.env`의 비밀번호만 바꾸면 DB 역할 비밀번호가 바뀌지 않습니다. 운영 DB의 역할 암호를 별도로 변경하고 서비스를 함께 갱신해야 합니다. 볼륨 삭제는 데이터를 영구 삭제하므로 실행 스크립트에 포함하지 않습니다.

## 개발 서버

Compose 웹은 완성 빌드입니다. 프런트 수정 시 Node 24로 `apps/web`에서 `npm ci`, `API_PROXY_TARGET=http://localhost:8088 npm run dev`를 사용합니다.

호스트 API 개발 시 별도의 로컬 PostgreSQL을 준비하고 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REGISTRATION_INVITE_TOKEN`을 환경 변수로 설정합니다. `services/api`에서 `./gradlew bootRun`으로 실행합니다. DB는 Flyway가 초기화하며 비밀 값은 명령 기록에 직접 입력하지 않는 방식을 권장합니다.

## HTTPS

운영 서버의 DNS를 실제 도메인에 연결하고 80·443 포트를 허용합니다. `.env`의 `DOMAIN`을 설정합니다.

```bash
docker compose --env-file .env -f infra/compose.local.yml -f infra/compose.prod.yml up -d --build --wait
```

Caddy가 인증서를 발급하고 웹으로 프록시합니다. 운영 세션 쿠키는 Secure이며 API는 prod 프로파일로 실행됩니다. 이 명령은 운영 배포를 자동 승인하는 절차가 아니며, 실제 운영 서버·도메인은 별도로 준비해야 합니다. 로컬 HTTP의 localhost 예외를 제외한 PWA 설치에는 HTTPS가 필요합니다.

현재 세션과 로그인 속도 제한은 API 프로세스 메모리에 존재합니다. API 재시작 시 재로그인이 필요하며 단일 API 인스턴스만 지원합니다. 다중 인스턴스 이전에 Spring Session JDBC와 공유 속도 제한 저장소를 적용합니다.

## 검증과 백업

CI는 웹 린트·타입·단위 테스트·빌드와 PostgreSQL 통합 테스트·API 빌드를 실행하도록 구성했습니다. GitHub의 실제 실행 결과는 push 후 확인합니다. iPhone Safari·Android Chrome의 홈 화면 설치, 실기기 키보드, 실제 TLS 인증서 발급은 해당 기기/도메인에서 검증해야 합니다.

운영 데이터 사용 전 PostgreSQL 백업과 별도 복원 테스트, 외부 보관 위치를 마련해야 합니다. 현재 구성에는 자동 백업이나 계정 삭제 기능이 없습니다. 상세 출시 게이트는 MASTER_SPEC 25장을 따릅니다.

운영 프록시 전용 네트워크는 `172.30.88.0/29`입니다. 기존 Docker/호스트 네트워크와 충돌하는 경우 Compose와 trusted-ingress.conf의 Caddy 주소를 함께 변경해야 합니다. 원본 IP 헤더는 해당 Caddy 주소에서 온 경우에만 신뢰합니다.

기존 Chrome이 설치되어 있으면 `E2E_BROWSER_CHANNEL=chrome npm run test:e2e`로 번들 Chromium 대신 사용할 수 있습니다. 자동 시작하는 브라우저 테스트 서버는 기존 개발 앱과의 충돌을 줄이기 위해 5187 포트를 사용합니다.
