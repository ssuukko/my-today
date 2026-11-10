# MY TODAY

오늘의 계획과 실제 활동, 돈과 성장을 하나의 공간에서 관리하는 개인 생활 운영체제입니다. PC·iPhone·Android에서 사용하는 반응형 PWA를 먼저 만듭니다.

상세 제품 설계서는 [docs/MASTER_SPEC.md](docs/MASTER_SPEC.md)에 원문을 보존했습니다. 화면 기준은 [DESIGN.md](DESIGN.md), 현재 구현 범위는 [docs/IMPLEMENTATION.md](docs/IMPLEMENTATION.md)를 참고하세요.

**기술 스택과 개발 상세:** [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)에 실제 사용 버전, 전체 구조, 기능별 구현·DB·보안·실행·테스트 방법을 정리했습니다.

## 현재 구현

Vue 3·Quasar·TypeScript 웹, Java 21·Spring Boot API, PostgreSQL·Flyway, 초대 가입·로그인·게스트 체험·CSRF·세션·최초 설정·로그아웃, ADMIN/USER 권한, 관리자 회원·초대 조회, 할 일·일정·성장·가계부·투자 관리, 오늘 대시보드, PWA 정적 자산 캐시, 로컬 Compose와 CI 구성을 제공합니다.

할 일은 생성·수정·완료·완료 취소·삭제와 날짜/상태/우선순위 조회를 지원합니다. 일정은 월/주/일 보기와 종일·시간 지정 일정의 생성·수정·삭제를 지원합니다. 수정 버전을 비교해 다른 기기의 변경을 덮어쓰지 않습니다. 오늘 화면은 사용자 시간대의 실제 일정과 할 일을 집계합니다.

성장은 실제 활동·시간 목표·요일 루틴·하루 회고를 기록합니다. 가계부는 계좌·수입·지출·이체·월 예산과 취소 이력을 관리합니다. 투자는 관심종목·수동 가격·매매 기록과 이동평균 원가 기반 손익을 제공합니다. 외부 실시간 시세·주문·OCR·이메일 검증/계정 복구와 공개 가입은 제공하지 않습니다. 오늘 홈은 아직 성장·금융 카드를 합산하지 않습니다.

## 로컬 실행

Docker Engine/Desktop과 Docker Compose v2.24.4 이상이 필요합니다. 컨테이너를 사용하는 경우 호스트의 Java·Node 설치는 필요하지 않습니다.

```bash
cp .env.example .env
```

`.env`에서 서로 다른 `POSTGRES_PASSWORD`, `DB_PASSWORD`와 추측하기 어려운 `REGISTRATION_INVITE_TOKEN`을 설정한 뒤 실행합니다. 비밀 값과 `.env`는 커밋하지 마세요.

```bash
./scripts/dev-up.sh
```

웹은 **http://localhost:8088**입니다. ‘초대받아 시작하기’에서 설정한 초대 코드로 가입하고, 로그인과 최초 설정을 마치면 오늘 화면으로 이동합니다. 기본 포트가 사용 중이면 `.env`의 `WEB_PORT`를 변경하세요.

```bash
./scripts/dev-down.sh
```

종료해도 DB 볼륨은 유지됩니다. 자세한 실행·개발·HTTPS 설정은 [운영 문서](docs/OPERATIONS.md)를 참고하세요.

현재 실행 중인 로컬 앱은 서울 리전의 무료 Supabase PostgreSQL에 연결되어 있습니다. [Supabase 연결 문서](docs/CLOUD_DATABASE.md)의 외부 DB 실행 명령으로 다시 시작할 수 있습니다. 위 기본 `dev-up.sh`는 원본 로컬 DB로 실행하며, 기존 로컬 계정·기록은 클라우드에 자동 이전하지 않았습니다.

관리자와 일반 사용자는 같은 로그인 화면을 사용합니다. 관리자는 `/admin`에서 회원·초대를 확인하고, 일반 가입은 항상 USER입니다. 현재 개발 환경의 초기 관리자 ID는 `admin@mytoday.local`이며 초기 비밀번호는 현재 PC의 보호 파일 `.env.admin`에만 저장했습니다. 공용 기본 비밀번호는 없습니다. 다른 환경의 최초 관리자 구성은 [권한 문서](docs/ACCESS_CONTROL.md)를 따릅니다.

로그인 없이 체험하려면 환영 또는 로그인 화면에서 ‘게스트로 체험하기’를 선택하세요. 개인별 임시 공간에서 기능을 사용할 수 있으며 24시간 후 접근이 만료됩니다. 게스트 기록은 일반 계정으로 이전되지 않습니다.

## 개발 및 검증

웹 개발에는 Node 24, API 개발에는 Java 21과 Docker가 필요합니다. Gradle은 Wrapper를 사용합니다.

```bash
cd apps/web
npm ci
npm run lint
npm run typecheck
npm test
npm run build
npm run dev
```

```bash
cd services/api
./gradlew test bootJar
```

백엔드 테스트는 Testcontainers로 독립적인 PostgreSQL을 실행합니다. 웹 개발 서버는 `/api`를 `localhost:8080`으로 전달합니다. API를 별도로 실행하거나 `API_PROXY_TARGET=http://localhost:8088 npm run dev`로 Compose의 API 프록시를 사용하세요.

```bash
cd apps/web
npx playwright install chromium
npm run test:e2e
```

기본 브라우저 테스트는 인증·계획·성장·가계부·투자의 편집, 충돌·장애·모바일 화면을 검증합니다. 실제 API 쓰기 테스트는 격리된 개발 DB에서만 테스트 계정과 기록을 만듭니다.

## 구조

| 경로 | 역할 |
|---|---|
| `apps/web` | Quasar 반응형 웹/PWA |
| `services/api` | Spring Boot 인증·프로필·계획·성장·가계부·투자 API와 마이그레이션 |
| `infra` | PostgreSQL, Nginx, Caddy HTTPS, Compose |
| `docs` | 제품 명세·실행 절차·API·보안 정책 |
| `.github/workflows` | API 및 웹 검사 |
