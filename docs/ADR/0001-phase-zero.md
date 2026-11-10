# ADR 0001: Phase 0 시작 구성

2026-11-02 · 채택

문서만 존재하는 저장소에서 MASTER_SPEC의 Phase 0을 먼저 구현한다. 모노레포, Java21/Spring Boot4.1.2의 모듈형 모놀리스, Vue3/Quasar/Vite8 PWA, PostgreSQL17/Flyway를 사용한다. 최초 모듈은 identity이며 존재하지 않는 도메인의 빈 서비스 계층은 생성하지 않는다.

개인 베타는 초대 토큰으로 가입을 제한한다. 공개 가입·메일 복구는 출시 조건을 충족할 때 추가한다. 기본 Spring Security 세션은 단일 API 프로세스 메모리이며 재시작 시 로그인이 만료된다. 지속 세션과 다중 인스턴스가 필요하면 Spring Session JDBC로 이관한다.

프런트는 Quasar Vite 플러그인을 사용하고 별도 vite-plugin-pwa로 정적 자산만 캐시한다. 플랫폼 전용 Capacitor·OCR·시세 라이브러리는 해당 구현 단계까지 추가하지 않는다. 인증 상태에만 Pinia를 사용하며 서버 도메인 조회가 늘어나면 명세의 Vue Query를 검토한다.

버전 근거: [Spring Boot 요구사항](https://docs.spring.io/spring-boot/system-requirements.html), [Quasar Vite](https://quasar.dev/start/vite-plugin/), [Spring Security 세션](https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html), [CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html), 공식 npm 레지스트리의 패키지 버전/peerDependencies. 정확한 버전은 Gradle과 package-lock.json으로 고정한다.
