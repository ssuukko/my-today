# MY TODAY — 개인 생활 운영체제 (Personal Life OS)

> **문서 종류:** 제품 요구사항 정의서(PRD) + 서비스 설계서 + 기술 아키텍처 + 데이터 모델/API 명세 + 개발 백로그  
> **문서 버전:** 1.2.0 (최초 로그인·회원가입·보안·온보딩·무료 PWA 우선 전략 추가)  
> **작성 기준일:** 2026-11-02 (Asia/Seoul)  
> **개발 저장소:** https://github.com/ssuukko/my-today  
> **문서 상태:** 구현을 위한 제안 명세. 확정된 구현이나 이미 개발된 기능을 의미하지 않음.  
> **핵심 전략:** 무료 PWA 우선 출시(PC·iPhone·Android 브라우저 홈 화면 설치) → 동일 계정·개인 데이터 동기화 → 네이티브 앱스토어 출시는 추후 별도 의사결정. Java/Spring Boot, Vue/Quasar, 영수증 카메라/OCR, 로그인 선행·개인정보 보호, 비용 최소화.

---

## 목차

1. [현재 상태와 제품 방향](#1-현재-상태와-제품-방향)
2. [비전·사용자·핵심 경험](#2-비전사용자핵심-경험)
3. [기능 범위와 단계 구분](#3-기능-범위와-단계-구분)
4. [정보 구조와 사용자 화면](#4-정보-구조와-사용자-화면)
5. [통합 홈 대시보드 상세](#5-통합-홈-대시보드-상세)
6. [일정·할 일·루틴](#6-일정할-일루틴)
7. [가계부·예산·자산](#7-가계부예산자산)
8. [주식·ETF·관심종목](#8-주식etf관심종목)
9. [실행 기록·목표·성장](#9-실행-기록목표성장)
10. [모듈 간 연결 및 자동화 규칙](#10-모듈-간-연결-및-자동화-규칙)
11. [검색·캡처·알림·인사이트](#11-검색캡처알림인사이트)
12. [사용성·접근성·디자인 시스템](#12-사용성접근성디자인-시스템)
13. [기술 스택과 설계 결정](#13-기술-스택과-설계-결정)
14. [백엔드 아키텍처와 이벤트 처리](#14-백엔드-아키텍처와-이벤트-처리)
15. [데이터 모델과 관계](#15-데이터-모델과-관계)
16. [핵심 SQL 마이그레이션 예시](#16-핵심-sql-마이그레이션-예시)
17. [REST API 설계](#17-rest-api-설계)
18. [외부 서비스 연동 및 제약](#18-외부-서비스-연동-및-제약)
19. [인증·보안·개인정보·백업](#19-인증보안개인정보백업)
20. [성능·관측·운영·비용](#20-성능관측운영비용)
21. [테스트와 수용 기준](#21-테스트와-수용-기준)
22. [단계별 개발 계획](#22-단계별-개발-계획)
23. [GitHub 프로젝트 구성과 개발 규칙](#23-github-프로젝트-구성과-개발-규칙)
24. [우선순위 백로그](#24-우선순위-백로그)
25. [출시 판단 기준과 미결 사항](#25-출시-판단-기준과-미결-사항)
26. [공식 참고 링크](#26-공식-참고-링크)
27. [웹·iOS·Android 멀티플랫폼 구현 명세](#27-웹iosandroid-멀티플랫폼-구현-명세)
28. [영수증 촬영·OCR·가계부 자동입력 명세](#28-영수증-촬영ocr가계부-자동입력-명세)
29. [최초 실행·로그인·회원가입·계정 복구 상세 명세](#29-최초-실행로그인회원가입계정-복구-상세-명세)

---

## 1. 현재 상태와 제품 방향

### 1.1 기존 프로젝트 확인

- 기존 `ssuukko/my-today` 저장소는 공개 `main` 브랜치이며, 확인 당시 루트에는 `README.md`만 존재한다.
- 기존 README의 **오늘 계획, 실제 활동, 성장 기록, 루틴**은 유지해야 할 핵심 제품 원칙이다.
- 실행 가능한 웹/서버/모바일 코드가 존재한다고 가정하지 않는다.
- 본 문서는 기존 README를 자동으로 변경하지 않는다. 구현 시 `docs/MASTER_SPEC.md`에 반영할 것을 권장한다.

### 1.2 문제 정의

일정은 캘린더, 할 일은 투두 앱, 지출은 가계부, 주가는 증권 앱, 목표는 노트 앱에서 각각 관리한다. 사용자는 정보를 반복 입력하고, 서로 다른 정보 간 연관성을 파악하기 어렵다. MY TODAY는 **사용자의 실제 행동을 기준으로 시간을 조직하고 돈과 목표까지 연결하는 단일 개인 대시보드**를 제공한다.

### 1.3 제품 철학

1. **오늘 중심:** 앱을 실행하면 가장 먼저 오늘을 이해하고 행동할 수 있어야 한다.
2. **연결 중심:** 하나의 행동이 일정·할 일·기록·예산 등 여러 맥락에서 재사용된다.
3. **입력 최소화:** 빠른 추가, 템플릿, 반복 규칙, 외부 데이터 연결을 제공한다.
4. **데이터 신뢰:** 예상/확정, 계획/실행, 잔액/수익률, 실시간/지연 시세를 반드시 구분한다.
5. **수정 가능:** 자동분류·추천·자동 연결 결과는 사용자가 언제든 수정·해제할 수 있다.
6. **프라이버시 우선:** 개인 데이터는 기본 비공개; 공개 범위·동의·삭제 기능을 기본 제공한다.
7. **단순한 첫 버전:** 마이크로서비스, AI 의존, 금융계좌 자동연동 등을 초기에 무리하게 구축하지 않는다.
8. **로그인부터 안전하게:** 최초 실행 시 로그인/회원가입으로 진입하고, 미인증 상태에서는 개인 일정·금융·투자 데이터에 접근할 수 없다.
9. **무료 PWA 우선:** PC·iPhone·Android에서 브라우저 홈 화면에 설치하여 사용하며, 유료 앱스토어 등록은 뒤로 미룬다.

### 1.4 핵심 지표(KPI) — 제품 가설

| 지표 | 정의 | 측정 방식 | 초기 목표/판단 |
|---|---|---|---|
| 오늘 준비율 | 하루를 시작하며 오늘 화면에 계획 1개 이상 있는 날 비율 | 사용자별 주간 집계 | 지속 사용자가 실제로 필요하다고 느끼는지 확인 |
| 실행 기록률 | 완료된 할 일 중 실행 기록과 연결된 비율 | 완료 과업/활동 관계 | 기록 강제 없이 증가 여부 관찰 |
| 빠른 입력 시간 | 할 일 또는 지출 1건을 저장하는 데 걸리는 시간 | UI 이벤트(내용 제외) | 일반적인 입력은 몇 번의 동작으로 완료 |
| 중복 데이터 비율 | 외부 동기화·가져오기로 생긴 중복 레코드 | 중복 키/감사 로그 | 중복 0건 유지 |
| 잔액 정합성 | 원장 합계와 계좌 잔액 일치 여부 | DB 정합성 테스트 | 불일치 0건 |
| 연동 복구율 | 토큰 만료·네트워크 장애 후 재연결 성공 | 백그라운드 작업 로그 | 복구 절차 제공 |
| 7일 재방문율 | 사용자가 자발적으로 재접속한 비율 | 익명화 집계 | 사용자 테스트 후 목표 수립 |

> 개인 단독 사용이라면 서비스 성장 KPI보다 **내가 실제로 반복해서 사용하는지, 잘못된 정보를 보여주지 않는지**가 더 중요하다. 숫자 목표는 출시 전 테스트를 통해 확정한다.

## 2. 비전·사용자·핵심 경험

### 2.1 목표 사용자

- **1차:** 나 혼자 쓰는 개인용 앱. 시간·지출·투자·성장을 한눈에 관리하려는 사용자.
- **2차:** 가족/지인 초대가 아닌, 각각 독립 데이터 공간을 가진 다중 개인 계정.
- **3차:** 공개 서비스 확장(구독형 포함) 검토. 이때 금융 시세 제공·외부 OAuth·개인정보 운영 규제가 별도 검증 대상.

### 2.2 대표 시나리오

**아침 08:00** — 오늘 일정 3건, 마감 할 일 2건, 오늘 쓸 수 있는 예산 24,000원, 관심 종목 가격 상태, 습관 2개를 하나의 홈에서 확인한다.

**점심 12:30** — `커피 5,000원`을 빠르게 입력하면 가계부와 하루 지출에 반영되고, 식비 예산이 재계산된다.

**오후 19:00** — `Java 공부 1시간` 할 일을 완료하면서 실제 45분을 기록한다. 오늘 실적·성장 통계는 실제 기록 45분만 사용한다.

**저녁 21:00** — 관심 종목 가격 알림을 확인하고, 이번 달 투자·현금 자산을 조회한다. 주식 시세가 오래되었으면 `지연` 또는 `업데이트 실패`를 표시한다.

**일요일** — 일정 이행률, 지출 추이, 가장 많이 투자한 시간, 루틴 달성 흐름을 주간 리뷰로 확인한다.

### 2.3 제품 경계: 서로 다른 개념을 구별한다

| 개념 | 의미 | 예시 |
|---|---|---|
| 일정(Event) | 특정 시각·기간에 잡힌 약속이나 블로킹 | 11/3 14:00 병원 |
| 할 일(Task) | 완료해야 할 행동 | 진료 예약 확인 |
| 루틴(Routine) | 정기적으로 반복할 행동의 정의 | 월~금 독서 20분 |
| 실제 활동(Activity) | 수행한 사실과 측정량 | 독서 18분 |
| 금융 거래(Transaction) | 자산에 영향을 준 실제 돈의 흐름 | 식비 12,000원 |
| 예상 현금흐름(Planned cash flow) | 아직 발생하지 않은 돈의 흐름 | 다음 달 월세 예정 |
| 주식 시세(Quote) | 특정 시점·제공처의 관측 가격 | AAPL USD 00.00, 조회시각 |
| 목표(Goal) | 기간 내 달성할 정량/정성 결과 | 한 달 공부 20시간 |

**금지 원칙:** 할 일 완료를 활동량으로 자동 중복 합산하지 않는다. 예정된 월급을 실제 잔액에 더하지 않는다. 현금 계좌와 주식 평가액을 자산 총액에 더할 때 동일 자금을 이중 집계하지 않는다.

## 3. 기능 범위와 단계 구분

| 기능 | M0: 사용 가능 MVP | M1: 통합성 완성 | M2: 확장/자동화 |
|---|---|---|---|
| 인증/첫 실행 | 로그인·회원가입·로그아웃·세션·최초 온보딩 | 계정 복구·기기별 세션 관리·Google 로그인 | 패스키/생체인증 및 보안 고도화 |
| 통합 홈 | 오늘 일정·할 일·지출·관심주식 카드 | 레이아웃 편집·주간 요약 | 상황별 스마트 카드 |
| 일정 | 수동 CRUD, 월/주/일 화면 | Google Calendar 읽기 동기화 | 양방향 연동·충돌 관리 |
| 할 일 | 생성·완료·마감·우선순위 | 하위 할 일·태그·반복 | 자연어 입력·자동 제안 |
| 루틴 | 요일 반복·수행 체크 | 목표량/예외일·통계 | 루틴 조정 제안 |
| 활동/성장 | 활동 생성·시간/횟수·통계 | 목표 연결·회고 | 개인 인사이트 |
| 가계부 | 수동 수입/지출/이체·카테고리 | 예산·고정비·월간 분석 | CSV 가져오기·자동 분류 |
| 자산 | 현금계좌별 잔액 | 대출·신용카드·순자산 | 금융회사 계좌 동기화(별도 검토) |
| 주식 | 관심종목·시세 표시 | 보유 수량·매수 기록·평가액 | 배당/분할·고급 차트 |
| 알림 | 마감/일정 로컬·서버 알림 | 예산/가격 임계치 | 스마트 묶음 알림 |
| 검색 | 통합 검색 | 고급 필터 | 의미 기반 검색 |
| 외부 연동 | 없음(수동 입력 우선) | 캘린더·가격 공급자 | 인증된 연동 확대 |
| AI | 없음 | 설명형 인사이트(규칙 기반 우선) | 선택적 생성형 AI |
| 플랫폼 | 반응형 웹 + 설치형 PWA(iPhone·Android·PC) | 오프라인 조회·홈 화면 설치 UX 개선 | 필요 시 Capacitor iOS·Android 스토어 배포 |

### 3.1 명시적 비범위(초기 제외)

- 주식 주문/자동매매, 투자 조언·종목 매수 추천, 금융계좌의 자동 입출금.
- 카드사/은행 웹사이트 크롤링, SMS·이메일 무단 수집, 주민번호/금융 비밀번호 저장.
- 처음부터 실시간 틱 데이터를 모든 사용자에게 무제한 제공하는 기능.
- 공동 가계부, 가족 공유 권한, 기업 일정, 공개 소셜 피드, 복잡한 세금 신고 자동화.
- 완전한 오프라인 양방향 편집·충돌 병합. M0는 온라인 쓰기, 네트워크 연결에 따른 오류 메시지부터 구현.

## 4. 정보 구조와 사용자 화면

### 4.1 정보 아키텍처(IA)

```text
MY TODAY
├─ 최초 진입 (/welcome) — 미로그인 사용자: 로그인/회원가입
├─ 로그인 (/login) / 회원가입 (/signup)
├─ 계정 복구 (/forgot-password, /reset-password) — 메일 연동 준비 후
├─ 초기 설정 (/onboarding) — 로그인 성공·최초 1회, 건너뛰기 가능
├─ 홈 (/today)
│  ├─ 오늘 타임라인 / 지금 다음 일정
│  ├─ 할 일·루틴 / 빠른 입력
│  ├─ 오늘 지출·남은 예산 / 현금 현황
│  ├─ 관심 주식 / 가격 시점
│  └─ 오늘의 기록 / 마감 리뷰
├─ 캘린더 (/calendar)
│  ├─ 월/주/일 뷰
│  ├─ 내 일정 / 외부 캘린더
│  └─ 일정 생성·상세·반복 설정
├─ 할 일 (/tasks)
│  ├─ 오늘 / 예정 / 밀린 일 / 완료
│  └─ 프로젝트 / 태그 / 우선순위
├─ 루틴·성장 (/growth)
│  ├─ 오늘의 루틴 / 활동 타이머
│  ├─ 목표·성장 통계
│  └─ 일간·주간·월간 회고
├─ 가계부 (/money)
│  ├─ 거래내역 / 빠른 기록 / 이체
│  ├─ 계좌·카드 / 예산·고정비
│  └─ 월간 리포트 / 순자산
├─ 투자 (/invest)
│  ├─ 관심종목 / 시세·차트
│  ├─ 내 보유종목 / 거래내역
│  └─ 가격 알림·제공처 상태
├─ 통합 검색 (/search)
├─ 알림함 (/notifications)
└─ 설정 (/settings)
   ├─ 내 정보 / 시간대·통화
   ├─ 카테고리 / 위젯 / 알림
   ├─ 외부 계정 / 데이터 가져오기·내보내기
   └─ 보안 / 연결 해제 / 계정 삭제
```

### 4.2 데스크톱 홈 와이어프레임

```text
┌──────────────────────────────────────────────────────────────────────────────────┐
│ MY TODAY   오늘   캘린더   할 일   성장   가계부   투자   [⌘K 검색]  [알림]    │
├──────────────────────────────────────────────────────────────────────────────────┤
│ 2026.11.01 일요일                      안녕하세요       [+ 빠른 기록] [설정]    │
│ 오늘 한 줄: "오늘 꼭 해야 할 일 1개를 정하세요"                                │
├──────────────────────────────────────────────────────────────────────────────────┤
│ [오늘 일정 3개]  [할 일 2/6]  [오늘 지출 18,500원] [이번 달 예산 62% 사용]     │
├─────────────────────────────────────┬────────────────────────────────────────────┤
│ 시간표 / 일정                       │ 오늘의 할 일                              │
│ 09:00 ● 출근                         │ □ API 공부 / 마감 오늘                     │
│ 12:00 ● 점심                         │ ☑ 메일 확인                               │
│ 18:30 ● 운동                         │ □ 카드값 확인                              │
│ [+ 일정]                             │ [+ 할 일]                                  │
├─────────────────────────────────────┼────────────────────────────────────────────┤
│ 내 돈                                │ 관심 주식                                  │
│ 오늘 지출 / 월간 예산 / 최근 거래     │ 종목 / 현재·종가 / 등락 / 기준시각         │
│ [+ 수입] [+ 지출] [+ 이체]            │ [종목 추가] [새로고침]                    │
├─────────────────────────────────────┴────────────────────────────────────────────┤
│ 오늘의 루틴     독서 ○  운동 ✓  공부 ○   |  실제 활동 45분 / 목표 60분          │
│ 오늘의 회고 한 줄: [________________________________________________] [저장]  │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**표시 값은 예시일 뿐 실제 사용자 데이터가 아니다.**

### 4.3 모바일 홈 와이어프레임

```text
┌────────────────────────────┐
│ MY TODAY        🔔     ⚙  │
│ 11월 1일, 일요일           │
│ [오늘] [주간] [월간]       │
├────────────────────────────┤
│ 다음 일정                 │
│ 14:00 진료               │
├────────────────────────────┤
│ 할 일 2 / 6    루틴 1 / 3 │
│ □ 자료정리    ✓ 물 마시기 │
├────────────────────────────┤
│ 오늘 지출    18,500원     │
│ 남은 월간 예산  xxx원     │
├────────────────────────────┤
│ 관심종목  업데이트 09:31  │
│ AAPL / 삼성전자 / ...     │
├────────────────────────────┤
│ [+] 무엇을 기록할까요?    │
├────────────────────────────┤
│ 홈   일정   할일   돈   더 │
└────────────────────────────┘
```

- 모바일 하단 탭은 최대 5개: `홈 / 일정 / 할 일 / 돈 / 더`. 투자·성장 메뉴는 `더`와 홈 카드에서 접근한다.
- 데스크톱은 좌측 사이드바 또는 상단 내비게이션. 가로 1024px 미만에서 단일 열/탭으로 전환한다.
- 빠른 입력은 언제나 최상단 또는 FAB로 접근한다.

### 4.4 화면 목록 및 최소 구성

| ID | 경로 | 반드시 있는 정보 | 기본 행동 |
|---|---|---|---|
| AUTH-UI-01 | `/welcome` | 서비스 소개·로그인·회원가입 버튼 | 로그인/회원가입으로 이동 |
| AUTH-UI-02 | `/login` | 이메일·비밀번호·오류·로그인 유지 | 로그인·비밀번호 표시 전환 |
| AUTH-UI-03 | `/signup` | 이메일·비밀번호·약관/정책 안내 | 계정 생성(공개 가입 시 이메일 검증) |
| AUTH-UI-04 | `/forgot-password` | 이메일 입력·복구 안내(메일 연동 후) | 재설정 요청 |
| AUTH-UI-05 | `/reset-password` | 새 비밀번호·확인·만료 안내 | 재설정 확정 |
| AUTH-UI-06 | `/onboarding` | 표시 이름·시간대·기본 통화·초기 목표 | 저장 후 `/today` 진입 |
| SCR-01 | `/today` | 오늘의 카드, 시간표, 주의 신호 | 빠른 입력 / 카드 상세 |
| SCR-02 | `/calendar` | 월/주/일 일정, 필터 | 일정 추가·수정·삭제 |
| SCR-03 | `/tasks` | 오늘/예정/밀린 할 일 | 완료·우선순위·마감 변경 |
| SCR-04 | `/growth` | 루틴 달성, 활동 합계, 목표 | 기록 시작·종료·수정 |
| SCR-05 | `/money` | 잔액, 거래내역, 예산 | 지출·수입·이체 입력 |
| SCR-06 | `/money/accounts` | 계좌별 잔액 및 유형 | 계좌 등록·조정 |
| SCR-07 | `/invest` | 관심종목, 마지막 가격 시점 | 종목 검색·추가·삭제 |
| SCR-08 | `/invest/assets` | 보유·원가·평가액·수익률 | 매매 기록 수동 입력 |
| SCR-09 | `/search` | 구분별 검색 결과 | 원본 화면으로 이동 |
| SCR-10 | `/settings` | 계정·통화·시간대·연동·데이터 | 설정 변경·내보내기 |

## 5. 통합 홈 대시보드 상세

### 5.1 홈 카드 및 데이터 계약

| 카드 | 데이터 원천 | 실시간성 요구 | 사용자에게 제공할 행동 |
|---|---|---|---|
| 오늘 일정 | `calendar_event` | 요청 직후 동기화된 서버 데이터 | 열기·수정·완료 아님 |
| 할 일 | `task` | 즉시 | 완료·연기·상세 |
| 루틴 | `routine_occurrence` | 즉시 | 수행·건너뛰기 |
| 오늘의 돈 | `money_entry` | 즉시 | 지출/수입/이체 |
| 이번 달 예산 | `budget` + 해당 거래 분류 | 즉시 | 예산 보기·수정 |
| 주식 | `market_quote` | 공급자 SLA에 따라 다름 | 보기·갱신·알림 |
| 성장 | `activity_log` | 즉시 | 기록 추가·수정 |
| 주간 인사이트 | 집계 테이블 또는 계산 | 비동기 집계 허용 | 상세·추천 해제 |

### 5.2 위젯 규칙

- 기본 홈은 개인 설정이 없어도 유용한 순서로 표출. 드래그 순서 변경은 M1.
- 모든 카드에는 **데이터 기준일/업데이트 시각**을 표시할 수 있어야 한다.
- 연동 실패 시 `정보 없음`을 `0원/0건`으로 둔갑시키지 않는다.
- 각 위젯은 개별 오류 격리: 시세 제공자가 다운되어도 할 일 화면은 정상 동작.
- 사용자가 위젯별 숨김/표시를 제어하고 설정은 서버에 저장한다.
- 홈 API는 집계 전용 BFF 성격. 하위 도메인 저장 명령은 홈 엔드포인트로 보내지 않는다.

### 5.3 오늘 상태 판정 예시

```text
긴급:  오늘 마감인데 완료하지 않은 할 일 2건
일정:  다음 약속까지 30분 (지금 시각 기준)
돈:    이번 달 식비 예산 90% 사용 (확정 거래만 기준)
투자:  관심 종목 목표 가격 조건 도달 (가격 시점 반드시 표시)
성장:  이번 주 공부 120/180분 달성 (실제 활동만)
```

알림 우선순위는 `사용자 지정 > 기한 임박 > 필요 행동 > 참고 정보`로 정하고, 단순한 주가 변화가 중요한 일정 알림을 밀어내지 않도록 한다.

## 6. 일정·할 일·루틴

### 6.1 일정(Calendar Event)

**필수 필드:** ID, 사용자 ID, 제목, 시작/종료, 하루 종일 여부, 시간대, 출처(내부/외부), 반복 규칙(선택), 생성·수정시각.

**선택 필드:** 메모, 장소, 태그, 색상, 미리 알림, 연결된 할 일, URL, 참석자(추후).

**검증 규칙:**

- 시간이 지정된 일정은 `end_at > start_at`; 날짜만 있는 일정은 시작일 포함·종료일 제외(`end_date_exclusive`)로 처리한다.
- 저장은 UTC `timestamptz`, 표시/반복은 사용자 지정 IANA 타임존(기본 `Asia/Seoul`)을 따른다.
- 해외 이동 시 일정의 **원래 시간대**와 표시 시간대를 혼동하지 않는다.
- 반복 일정은 RRULE 또는 반복 규칙과 개별 예외(`recurrence_exception`)를 분리한다.
- 반복 1회만 수정/삭제할 수 있어야 하며, 시리즈 전체 수정과 구분한다.
- 동일 외부 일정이 중복 생성되지 않도록 `(provider, external_calendar_id, external_event_id, recurrence_id)`로 식별한다.
- 외부에서 불러온 읽기 전용 일정은 수정 버튼을 제한하고 원본으로 이동 링크 제공.

**수용 조건:** 월/주/일 이동, 일정 생성·수정·삭제, 하루종일 일정, 시간 겹침 표시, 타임존 일관성, 반복 예외일 처리.

### 6.2 할 일(Task)

| 필드 | 예시 | 규칙 |
|---|---|---|
| title | 프로젝트 명세 작성 | 공백 불허, 길이 제한(예 1~200자) |
| status | TODO / IN_PROGRESS / DONE / CANCELED | 상태 변경 시각 기록 |
| due_at | 2026-11-02T08:00:00+09:00 | 날짜만 마감이면 별도 `due_date` 사용 |
| priority | P0, P1, P2, P3 | 정렬을 위해 숫자/열거값 고정 |
| estimated_minutes | 60 | 선택 항목. 실제 기록과 분리 |
| project_id | 개발 | 프로젝트 사용 안 해도 가능 |
| calendar_event_id | optional | 예약 시간과 업무 완료는 별개 |
| activity_category_id | 개발 | 실제 활동 연결용 |
| completed_at | timestamp | DONE일 때만 설정 |

**할 일 처리 규칙:**

1. 단건 완료 시 해당 작업만 DONE, 과거 기록 유지.
2. 반복 작업은 템플릿(`task_template`)과 날짜별 인스턴스(`task`)를 분리하고, 중복 생성 방지 키 적용.
3. 업무를 연기해도 과거 원래 마감 날짜의 사실이 사라지지 않도록 변경 기록 선택 저장.
4. 일일 체크리스트가 완료되었다고 실제 수행 시간이 자동으로 발생하지 않는다.
5. 완료 후 실제 활동을 입력할 수 있으며, 이때 동일 작업의 동일 활동이 중복 생성되지 않도록 연결 키를 사용한다.
6. 삭제는 기본 소프트 삭제; 오입력 복구 가능 기간을 둔다.

### 6.3 루틴(Routine)

- 반복 정의: 제목, 시간대, 요일 집합, 활성 시작/종료일, 목표량(`target_value`, `unit`), 활성 여부.
- 날짜별 발생: `routine_occurrence`에 `scheduled_date`, `target_snapshot`, `status`, `completed_at`, `skip_reason` 저장.
- 상태: `PENDING / DONE / SKIPPED / MISSED`; `SKIPPED`를 실패로 간주할지 여부는 통계 옵션으로 결정.
- 과거 루틴 목표량 보존: 루틴의 현재 목표가 20→40분으로 바뀌어도 과거 달성률은 당시 목표 20분 기준.
- 지나간 날짜의 자동 `MISSED` 전환은 사용자 현지 날짜가 완전히 지난 후 백그라운드 처리.
- 미리 인스턴스를 무제한 생성하지 않고, 요청된 범위에 대해 멱등 materialization 수행.

**루틴/할 일 차이:** 루틴은 재발생 정의, 할 일은 특정 실행 단위. 루틴 발생 항목을 할 일 카드에 함께 보여줄 수 있지만 **서로 다른 타입/키**로 구분한다.

### 6.4 시간 충돌 알림

- 같은 시간에 두 일정이 있으면 겹침 표시. 자동으로 일정 변경하지 않는다.
- 집중시간 블록과 일정이 겹치면 사용자에게만 충돌 경고.
- 일정을 수정했을 때 연결된 할 일 마감을 무조건 이동하지 않고 `연동 변경` 여부를 묻는다.

## 7. 가계부·예산·자산

### 7.1 금융 기능 목표

수입/지출 단순 메모가 아니라 **계좌별 잔액과 분류별 소비·월별 예산을 신뢰할 수 있는 개인 원장**으로 만든다.

### 7.2 계좌(Account)

종류: `CASH`, `CHECKING`, `SAVINGS`, `CREDIT_CARD_LIABILITY`, `LOAN_LIABILITY`, `BROKER_CASH`, `OTHER_ASSET`.

- 계좌에는 통화(`KRW`, `USD`, `JPY`), 이름, 최초 기준 잔액, 개설·활성 상태가 있다.
- `현재 잔액 = 시작 잔액 + 모든 확정 원장 증감의 합`; 애플리케이션은 단순 입력 금액을 수시로 덮어쓰지 않는다.
- 신용카드 부채는 자산이 아닌 부채. 사용 시 카드 부채 증가 및 비용 인식, 결제 시 부채 감소와 은행현금 감소.
- 증권계좌 현금과 주식 평가액은 자산 합산 시 구분한다.
- 대출 상환은 원금 감소와 이자 비용을 나누어 기록한다.

### 7.3 원장 기본 모델: 두 계정 간 이중 기록

재무 정합성을 위해 `money_transaction`(거래 헤더) + `money_posting`(계정별 변화)을 권장한다. 사용자가 입력하는 한 건의 거래는 내부적으로 2개 이상의 posting이 될 수 있다.

예) `식비 12,000원`을 A은행 계좌에서 지불:

```text
Transaction EXPENSE #101
  Posting: A은행 CASH             -12,000 KRW
  Posting: 식비 비용범주(가상계정) +12,000 KRW
```

예) A은행→B은행 50,000원 이체:

```text
Transaction TRANSFER #102
  Posting: A은행 CASH             -50,000 KRW
  Posting: B은행 CASH             +50,000 KRW
```

예) 10만 원 체크카드 사용을 6만 원 식비 + 4만 원 쇼핑으로 분할:

```text
Transaction EXPENSE #103
  Posting: A은행 CASH            -100,000 KRW
  Posting: 식비 비용범주           +60,000 KRW
  Posting: 쇼핑 비용범주           +40,000 KRW
```

**설계 주의:** 비용범주용 가상 계정은 현금성 자산에 포함하지 않는다. 같은 통화인 거래의 posting 합은 0이어야 한다. 외환거래는 환전 브리지와 적용 환율/수수료를 사용해 통화별 합계를 맞추며, 통화가 다른 posting 숫자를 직접 더해 0인지 검사하면 안 된다. 모든 posting은 서버 트랜잭션 내 생성·확정한다.

### 7.4 실제 결제 vs 예정 결제

- 실제/확정: `money_transaction.status=POSTED`, 잔액과 지출 통계에 포함.
- 임시/승인 대기: `PENDING`, 별도 잠정 표시; 기본 잔액 통계에 포함하지 않음.
- 예정 월세/구독: `recurring_bill` 또는 `planned_cashflow`, 미래 예측에만 사용.
- 수입 예정은 실제 입금이 확인되기 전까지 소득 합계·현금 잔액에 미반영.
- 같은 카드 내역을 수동 입력 후 CSV로 가져오면 자동 병합하지 말고 중복 후보를 비교·확정.

### 7.5 카드 지출과 결제일 처리

1. 신용카드 사용 시 비용 인식 + 카드 부채 증가(월간 소비 통계 포함).
2. 카드 결제일에 은행계좌 현금 감소 + 카드 부채 감소(소비 재인식 금지).
3. 카드대금 결제를 `이체/부채상환`으로 취급해야 월별 지출이 2번 잡히지 않는다.
4. 할부는 결제/청구 스케줄과 소비 발생시점을 분리하고, M1에서 구현한다.
5. 취소/환불은 기존 거래 수정으로 이력을 지우지 않고 원 거래 참조한 환입/반대 거래를 생성한다.

### 7.6 카테고리와 예산

- 카테고리: 식비/카페/교통/쇼핑/생활/통신/주거/구독/건강/교육/여행/기타 등; 사용자가 생성·이름 변경 가능.
- 예산 단위: 월별 또는 사용자 지정 기간; 카테고리별 한도, 통화, 이월 여부, 알림 임계치.
- 한도 300,000원, 확정 지출 180,000원이라면 사용률 60%, 잔여 120,000원.
- 환불은 관련 카테고리 지출에서 상쇄하고, 예산 음수/초과 상태는 명확히 표시.
- `예산 잔액`과 `은행계좌 잔액`은 다른 값임을 UI에서 분명하게 표기.
- 80%·100% 등 임계치 통과 알림은 최초 1회 또는 설정한 주기로 제한.

### 7.7 환율·다중 통화

- 기본 리포트 통화는 KRW. 거래의 원화 환산 값은 거래 당시 적용 FX 기준과 시점을 보존.
- 통화 금액 저장은 `NUMERIC(20, 6)` 등 정밀 숫자를 사용; Java `BigDecimal`, JavaScript 문자열 기반 십진수 라이브러리 사용.
- 현재 순자산을 합칠 때 필요한 환율은 `fx_rate`의 기준시각/제공처/유효성을 함께 표시.
- 환율 미확보 시 전체 평가액을 추정치로 표시하거나 해당 통화를 별도 제공; 무단으로 1:1 변환하지 않음.
- 투자 실현손익과 가계부 수입은 별도의 회계 정책이 있어야 한다. 매도대금 전체를 `수입`으로 표시하지 않는다.

### 7.8 가계부 리포트

- 일/주/월 소비, 카테고리 Top N, 전월 대비(기준 통화 일치), 고정/변동비, 예정 지출.
- 기본 통계에서 **계좌 간 이체·카드대금 납부·투자 원금 이동** 제외.
- 순자산 = 현금성 자산 + 평가 투자자산 + 기타 자산 − 카드 부채 − 대출 부채; 동일 자산 중복 제외.
- 빈 기간과 0원 기간을 구별한다. 수정/삭제/환불은 집계를 다시 계산한다.

### 7.9 영수증 자동입력 연계

- 사진 촬영 또는 이미지 업로드로 영수증의 가맹점·거래일시·최종 결제금액·세금/할인·품목을 추출한다.
- OCR 인식 결과는 거래 확정이 아니라 **검토용 임시 초안**이다. 금액을 사용자가 확인하고 저장해야 원장에 반영된다.
- 영수증과 기존 수동 입력·카드/계좌 가져오기 거래가 일치할 수 있으므로 중복 후보를 탐지하고 연결·건너뛰기·새 거래 생성을 선택하도록 한다.
- `receipt_import`와 `money_transaction`은 분리하되 확정된 거래만 `money_posting`에 반영한다. 상세 정책은 **28장** 참조.

## 8. 주식·ETF·관심종목

### 8.1 지원 목표

- 국내/미국 종목 관심목록 등록 및 검색.
- 현재/종가(공급자 지원 범위), 등락률·장 상태·데이터 기준시각 표시.
- 원하는 가격 도달 시 알림(설정/쿨다운 필요).
- 사용자의 수동 매수·매도·수량·수수료·세금 입력 후 보유현황과 수익률 계산.
- 주식에 관한 공시·배당·분할은 M2. 매매 실행 기능은 제외.

### 8.2 종목 정체성

종목명 문자열만 키로 사용하지 않는다. `instrument_id`(내부 PK), `market`(KRX/NASDAQ/NYSE 등), `symbol`, `currency`, `asset_type`, `provider_symbol`, 유효기간을 분리한다. 동일 티커가 여러 거래소에 존재할 수 있다.

### 8.3 시세 모델

```json
{
  "instrumentId": "7d3d...",
  "symbol": "AAPL",
  "exchange": "NASDAQ",
  "price": "245.31",
  "currency": "USD",
  "change": "1.25",
  "changePercent": "0.51",
  "provider": "example-provider",
  "marketState": "OPEN",
  "quoteType": "REALTIME",
  "quotedAt": "2026-10-30T14:01:00Z",
  "receivedAt": "2026-10-30T14:01:02Z",
  "stale": false
}
```

> 위 값은 **API 형식 설명을 위한 임의의 샘플**이며 2026-11-01 실제 시장 시세가 아니다.

- `quoteType`: `REALTIME / DELAYED / END_OF_DAY / MANUAL / UNKNOWN`.
- `quotedAt`: 제공처에서 관측한 시간, `receivedAt`: 내 서버 수신 시간. 서로 다르게 저장.
- 거래소 휴장일·장 종료 후 오래된 값은 잘못된 실시간 시세로 오인되지 않게 `마지막 종가`로 표시.
- 시세가 실패할 경우 직전 캐시 값을 사용하되 `오래된 가격` 배지·기준시각 표시; 처음부터 시세가 없다면 미제공.
- 차트는 일별 시세를 우선 사용하고 분봉은 공급자 권한/비용/저장 조건 확인 후 도입.

### 8.4 평가금액·손익 계산

- `평가금액(현지통화) = 보유수량 × 기준시각의 시세`.
- `평가손익(현지통화) = 평가금액 − 현재 남은 원가(매도/분할 반영)`.
- `평가수익률 = 평가손익 ÷ 남은 원가 × 100` (남은 원가 0이면 N/A).
- `실현손익 = 매도 순대금 − 매도 수량에 배분된 취득원가`.
- 평균매입법/선입선출 등 취득원가 정책은 설정 전 반드시 확정하고 과거 계산 정책을 보존.
- 수수료·세금·외화 환산손익은 별도 항목으로 추적. 단순 `현재가 - 매수가`를 원화 수익률로 오도하지 않는다.
- 배당, 액면분할, 합병, 입출고는 나중에 투자 원장을 확장해 반영. M1에서 지원하지 않는 경우 사용자에게 `추정` 표시.

### 8.5 가격 알림 규칙

- 조건: `ABOVE`, `BELOW`, `PERCENT_CHANGE`(선택).
- 가격 도달 판정은 **제공처 데이터가 새롭게 수신되었을 때만** 실행한다.
- 최초 충족 시 알림 1회, 이후 `COOLDOWN`과 `REARM`(가격이 다시 임계치 반대편으로 이동해야 재무장) 조건 사용.
- 장 마감/지연 데이터로 인한 중복 알림, 휴장 중 오탐 방지.
- 알림 메시지에는 `종목명, 조건, 관측 가격, 통화, 시각, 시세 유형` 포함.

### 8.6 법적·상업적 핵심 경계

**한국투자증권 KIS Open API**는 국내외 주식 REST/WebSocket 시세 기능을 제공하지만, 공식 안내상 개인 시세는 본인의 투자 목적 사용을 전제로 하며 **제3자 제공은 불가**하다. 그러므로 **나 혼자 쓰는 앱**과 **일반 사용자가 가입하는 공개 서비스**의 데이터 사용권은 구분해야 한다. 공개 서비스 전환 시 거래소/코스콤/제공처의 라이선스·재배포 허가를 별도로 확인해야 한다. 제휴형은 계약·자격 조건이 다르다. 또한 API 호출 제한은 공지에 따라 변한다. 상세 내용은 18장과 공식 링크 참조.

**중요:** 무료 API가 존재한다는 사실만으로 앱 화면에 재배포할 권리, 무제한 저장 권리, 상업적 제공 권리가 생기지 않는다.

## 9. 실행 기록·목표·성장

### 9.1 활동(Activity Log)

- 원천: 직접 기록 또는 할 일/루틴에서 `실제 활동 기록` 생성.
- 필드: 활동 분야, 수행 시점, 실제 지속시간(초/분), 수량, 단위, 기록 메모, 연결 task/routine/goal.
- 동일 단위끼리만 누적. `독서 20분 + 책 30페이지`를 50이라는 성장점수로 임의 환산하지 않는다.
- `duration_seconds`와 `quantity_value/quantity_unit`은 구분 저장.
- 계획 60분/실제 40분인 경우 완료 여부와 목표 달성률이 다를 수 있다.
- 업무 완료 자체와 활동 기록은 서로 다른 이벤트. UI는 연동 상태를 보여준다.

### 9.2 목표(Goal)

| 종류 | 예시 | 자동 집계 원천 |
|---|---|---|
| 시간 합계 | 11월 Java 공부 20시간 | `activity_log` 내 개발 카테고리 시간 |
| 실행 횟수 | 운동 12회 | 독립 activity ID/루틴 실적 |
| 숫자 누적 | 독서 300페이지 | 페이지 단위 활동 |
| 지출 상한 | 한 달 카페 80,000원 이하 | 가계부 확정 분류 거래 |
| 저축 목표 | 비상금 300만 원 | 선택한 계좌의 실제 잔액/입금 실적 |
| 체크리스트 | 포트폴리오 완성 | 연결된 마일스톤 상태 |

- 목표 집계 공식과 데이터 원천이 노출되어야 한다.
- 기록 수정 시 기계적으로 재집계.
- 수치 목표 외 정성 목표는 사용자 승인/메모로 완료 처리.
- 역방향 목표(지출 이하)와 정방향 목표(공부 이상)는 달성 기준이 다르다.

### 9.3 회고

- 하루: `잘한 일 / 놓친 일 / 내일 하나 바꿀 점` 선택 작성.
- 주간: 실제 사용시간 상위 분야, 할 일 완료율, 지출 추세, 루틴 수행률 자동 정리.
- 월간: 목표 진행률 및 예상 대비 실적, 지출·투자 평가액 추이(과도한 투자 성과 압박 없이).
- AI 없이 규칙 기반 설명으로 시작. 이후 생성형 AI 사용 시 개인 거래내역/민감정보 전송 동의를 별도 받는다.

## 10. 모듈 간 연결 및 자동화 규칙

### 10.1 연결은 '원본 1개 + 참조 여러 개'

| 트리거 | 원본 도메인 | 파생 액션 | 주의 |
|---|---|---|---|
| 할 일 완료 | Task | 오늘 카드 갱신, 선택 시 Activity 생성 제안 | 활동을 자동 중복 생성하지 않음 |
| 루틴 수행 완료 | Routine | 수행 실적 업데이트, 연결 Activity 1건 생성(옵트인) | `(occurrence_id, activity_type)` 멱등 |
| 지출 등록 | Money | 일일 지출·예산 카드 다시 계산 | 예산의 지출 기준은 확정거래 |
| 급여 입금 | Money | 수입 집계, 현금 잔액 갱신 | 예정 급여는 제외 |
| 카드 결제 | Money | 부채/현금 조정 | 비용 두 번 인식 금지 |
| 주가 수신 | Invest | 관심종목 카드 갱신, 가격 알림 판정 | 시세 신선도/권한 확인 |
| 일정 생성 | Calendar | 홈 시간표 반영 | 업무 자동 완료 금지 |
| 일간 마감 | Review | 오늘 회고 초안 집계 | 뒤늦은 수정 시 재집계 |

### 10.2 사용자 규칙 예시 (M2)

```yaml
id: notify-budget-80
when:
  domain: money
  event: budget.threshold_crossed
  condition:
    greater_or_equal: 80
then:
  - type: notify
    channel: in_app
    cooldown: P1D
```

이 YAML은 **아이디어/설정 명세**이며 실제 구현된 룰 엔진 문법이 아니다. 초기에는 코드 기반 `BudgetThresholdPolicy` 등으로 명확하게 구현하고, 임의 사용자 코드를 평가하는 런타임을 만들지 않는다.

### 10.3 통합 모델 원칙

- 모듈 간 직접 테이블 수정 금지. 하나의 모듈은 자신의 데이터를 변경하고 이벤트/서비스 인터페이스로 타 모듈과 통신.
- 모든 자동 행동의 트리거·결과·실패를 `automation_execution`으로 감사 가능하게 설계.
- API를 통한 저장은 원자적으로 처리, 외부 전송과 알림은 `outbox_event` 기반 비동기 처리.
- 실패 재시도는 중복 결과가 생기지 않게 소비자 처리 ID를 기록한다.
- AI가 작성한 행동 계획은 읽기/제안이 기본; 송금·매매·삭제 등 고위험 행위는 자동 실행 불가.

## 11. 검색·캡처·알림·인사이트

### 11.1 글로벌 빠른 입력

사용자가 상단 `+` 또는 `Ctrl/Cmd+K`로 아래 중 하나를 선택한다.

```text
할 일       "토요일까지 서버 배포"
일정       "11/5 15:00 치과"
지출       "점심 9,500원"
수입       "외주비 200,000원"
활동       "독서 25분"
관심종목   "AAPL"
영수증     "촬영/앨범/파일에서 자동입력"
```

- M0: 입력 유형을 사용자가 선택하고, 타입별 간단 폼으로 확정.
- M2: 자연어 파싱으로 필드 후보를 표시하되 **저장 전에 확인**.
- 입력 중 네트워크 오류 시 폼 내용 유지. 재시도는 중복 저장하지 않도록 `Idempotency-Key` 사용.

### 11.2 통합 검색

- 검색 범위: 일정/할 일/활동/거래 메모/종목/목표.
- 공통 검색 결과: `type, id, title, snippet, occurredAt, targetUrl`.
- 사용자별 격리 필수. 다른 계정 데이터 검색 불가.
- 금액·메모 등의 민감 데이터를 외부 검색 SaaS에 기본 전송하지 않는다.
- PostgreSQL Full Text Search 또는 `pg_trgm` 기반 검색 우선.

### 11.3 알림 구분

| 종류 | 채널 | 기본값 | 중복 방지 |
|---|---|---|---|
| 일정 시작 전 | 인앱 / PWA 푸시(지원 시) | 선택적 | 일정 ID + 알림시점 |
| 할 일 마감 | 인앱 | 켜짐 | 할 일 ID + 마감 상태 |
| 루틴 미완료 | 인앱 | 선택적 | 날짜 + 발생 ID |
| 예산 임계치 | 인앱 | 켜짐 | 예산 + 단계(80/100%) |
| 주가 조건 도달 | 인앱 | 사용자가 직접 설정 | 알림규칙 + 재무장 상태 |
| 월말 리뷰 | 인앱 | 선택적 | 사용자 + 기준 월 |

- 기본 조용한 시간(quiet hours)은 설정 가능; `Asia/Seoul` 기준으로 계산.
- 웹 푸시 권한 거부 시 인앱 알림만 사용.
- 동일한 이벤트를 이메일/푸시/인앱으로 보낼 때 사용자가 채널별 수신 여부 선택.
- 모바일 푸시는 OS/브라우저 정책과 설치 조건에 따라 지원 차이가 있다.

### 11.4 인사이트: 반드시 설명 가능한 규칙 기반부터

- `이번 주 카페 지출은 지난주보다 18% 많음` — 같은 분류·같은 통화·기간 비교 조건 충족 시만 표시.
- `목요일에 할 일이 자주 밀림` — 표본 수와 집계 기간을 함께 표시.
- `공부 시간을 저녁에 많이 확보함` — 실제 활동 시작시각과 수행분 기준.
- `월말 예상 현금 잔액` — `현재 확정 잔액 + 확정/예정 미래 현금흐름`의 시나리오임을 명시.
- `주가 변동이 크다` 등 투자 해석은 객관적 변화와 데이터 기준시각만 서술; 매수·매도 지시형 문구 금지.

## 12. 사용성·접근성·디자인 시스템

### 12.1 디자인 토큰(초기안)

| 토큰 | 라이트 | 다크 | 설명 |
|---|---|---|---|
| background | `#F7F9FC` | `#10151E` | 앱 배경 |
| surface | `#FFFFFF` | `#19212B` | 카드/모달 |
| text-primary | `#1A2433` | `#F0F3F8` | 주요 텍스트 |
| text-secondary | `#5A687B` | `#AAB5C3` | 설명 |
| brand | `#3665DB` | `#7E9DFF` | 주요 액션 |
| success | `#227A52` | `#5CCB95` | 정상/완료 |
| warning | `#9B6400` | `#F1BC55` | 주의 |
| error | `#C7394A` | `#FF7F8D` | 오류 |
| radius | `12px` | 동일 | 카드 |
| spacing | `4/8/12/16/24/32px` | 동일 | 일관된 간격 |

색상은 제안이며 접근성 대비 검사 후 조정한다. 주식 상승/하락은 색상만으로 표현하지 않고 `+/-`, 화살표, 숫자와 함께 표시한다. 한국/미국 주가 상승·하락 색상 문화가 다른 점을 고려해 색상 설정을 사용자화할 수 있다.

### 12.2 반응형 기준

- 360px 이상 모바일 세로 레이아웃, 768px 전후 태블릿, 1024px 이상 데스크톱 다중 열.
- 터치 타깃 최소 44×44 CSS px 권장; 키보드 탐색/포커스 표시.
- 명암 대비, 글자 확대, 화면 읽기 레이블, 입력 에러 설명, `prefers-reduced-motion` 대응.
- 개인정보가 포함된 홈 위젯은 미리보기/잠금화면/공유 스크린샷 노출 옵션 고려.
- 날짜와 금액 표기는 사용자 locale. 천단위 구분, 마이너스·통화 부호, 데이터 기준시각 명확히 표시.

### 12.3 UX 사용 원칙

1. 할 일 완료/지출 입력 등 자주 쓰는 행동은 **2~3단계 이내**.
2. 삭제·대량 가져오기 등 되돌리기 어려운 행동은 확인/되돌리기 지원.
3. 빈 화면은 `데이터 없음` + 첫 행동 CTA; 로딩/오류/비어 있음 상태 구분.
4. 계산 값은 클릭하면 **어떻게 계산했는지** 확인 가능.
5. 홈의 정보 밀도를 사용자가 선택: 간결 모드와 상세 모드.
6. 시작 시 시세/재무 연동 강제 금지. 할 일만 사용해도 정상 동작.

## 13. 기술 스택과 설계 결정

### 13.1 권장 기술 스택

| 영역 | 권장 | 선택 이유 | 대체/주의 |
|---|---|---|---|
| Backend | **Java 21 LTS + Spring Boot(개발 시점의 지원 중인 안정 버전 고정)** | Java 경험 활용, 테스트/인증/배치 생태계 | 처음부터 과도한 버전 추격 금지 |
| Web | **Vue 3 + TypeScript + Quasar** | 웹 UI와 PWA, 후속 Capacitor 기반 모바일 확장에 적합 | 실제 iOS/Android 네이티브 경험은 플랫폼별 검증 필요 |
| Mobile | **Quasar PWA 우선, Capacitor 후속 선택** | iPhone·Android·PC에서 홈 화면 설치, 앱스토어 비용 보류 | 네이티브 푸시/키체인/앱스토어 기능은 후속 별도 검증 |
| DB | **PostgreSQL 16+ 계열의 유지보수 지원 버전** | ACID, JSONB, 인덱스, 정밀 NUMERIC, 검색 | 선택 버전은 배포 환경 호환성 확인 |
| Persistence | Spring Data JPA + QueryDSL 또는 명시적 SQL(JdbcClient) | 일반 CRUD 간결·복잡한 리포트 SQL 제어 | 금융 원장은 명시적 쿼리/트랜잭션 우선 |
| Cache | Spring Cache + Caffeine (초기) | 별도 서버 불필요 | 다중 인스턴스로 확장 시 Redis 검토 |
| Migration | Flyway | DB 스키마 버전 관리 | 운영 데이터 수동 수정 금지 |
| API contract | Springdoc OpenAPI + 코드 생성(선택) | 웹-서버 타입 호환성 | Spring Boot 주 버전과 라이브러리 호환 확인 |
| Security | Spring Security, 이메일/비밀번호(M0) + 서버 세션 + CSRF 보호 | PWA의 무료 독립 계정 로그인, HttpOnly 쿠키 | Google OIDC·패스키·네이티브 OAuth는 후속 |
| Jobs | Spring Scheduler + DB 작업 잠금 | 단일 인스턴스에서 가볍게 시작 | 확장 시 ShedLock/작업 큐 검토 |
| Chart | Apache ECharts 또는 Chart.js | 자산·지출·시세 차트 | 사용한 라이브러리 라이선스 확인 |
| UI State | Pinia + Vue Query(TanStack Query Vue) | 캐시/서버 상태 분리 | 도메인 데이터 원본은 서버 |
| Test | JUnit 5, AssertJ, Testcontainers, Vitest, Playwright | DB 정합성·UI 회귀 검증 | 테스트 환경은 운영 데이터 사용 금지 |
| Deploy | Docker Compose + Nginx/Caddy + HTTPS | 개인 서버·소형 VPS에 적합 | 백업을 같은 서버에만 두지 말 것 |
| Monitoring | Actuator + Micrometer + 구조화 로그 | 장애 원인 파악 | 고비용 모니터링 SaaS 의무 아님 |
| CI/CD | GitHub Actions | 자동 테스트/빌드/배포 | 비밀 키를 GitHub Actions Secret으로 보호 |

**판단:** 사용자의 Java 백엔드 개발 역량과 웹 먼저 → 모바일 확장·비용 절약이라는 요구를 고려하면 **Spring Boot + Vue/Quasar + PostgreSQL의 모듈형 모놀리스**가 출발점으로 적합하다. React Native/Expo는 모바일 완성도가 최우선일 때 별도 ADR로 재평가한다.

### 13.2 왜 마이크로서비스가 아닌가?

- 핵심 도메인이 6~8개여도 개발/운영 인원이 적다면 마이크로서비스 배포·분산 트랜잭션·관측성 부담이 지나치게 크다.
- 도메인별 Java 패키지, 서비스 인터페이스, DB 스키마 접근 규칙을 갖춘 모듈형 모놀리스로 시작한다.
- 시세 업데이트만 병목이라면 **별도 워커 프로세스**로 먼저 분리한 뒤 필요할 때 서비스 분리를 검토한다.

### 13.3 권장 서버 구성

```mermaid
flowchart TB
    A[Web / PWA / Future Mobile] -->|HTTPS| B[Reverse Proxy]
    B --> C[Spring Boot API]
    C --> D[(PostgreSQL)]
    C --> E[Caffeine Cache]
    C --> F[Domain Modules]
    F --> F1[Calendar / Task / Routine]
    F --> F2[Money / Budget]
    F --> F3[Investment / Market Data]
    F --> F4[Activity / Goal / Notification]
    C --> G[Background Jobs + Outbox Worker]
    G --> H[Google Calendar API]
    G --> I[Licensed Market Data APIs]
    G --> J[Push/Email Provider]
    K[Monitoring / Backup] --> D
    K --> C
```

### 13.4 배포 환경

| 환경 | 목적 | 데이터 |
|---|---|---|
| Local | 개발자 PC에서 단위/통합 테스트 | 가짜 데이터 |
| Staging | 운영 유사성 검증, 외부 API 샌드박스 | 익명화/테스트 데이터 |
| Production | 실제 사용자 기록 | 개인 데이터 암호화·백업·접근 제한 |

- 로컬 Docker Compose는 웹/서버/DB를 한 번에 실행하는 개발 경험을 목표로 한다.
- 운영은 애플리케이션 DB 사용자가 `CREATEDB`, 슈퍼유저 권한을 갖지 않도록 한다.
- 도커 이미지 버전/체크섬 고정; 운영 이미지는 자동으로 최신 태그를 당겨 쓰지 않는다.

## 14. 백엔드 아키텍처와 이벤트 처리

### 14.1 모듈 구분

```text
com.mytoday
├── MyTodayApplication.java
├── common
│   ├── config / security / exception / audit / paging
│   └── time / money / idempotency / outbox
├── identity         # 사용자, 환경설정, 로그인, 연결 계정
├── calendar         # 일정, 반복, 외부 캘린더 동기화
├── task             # 할 일, 프로젝트, 템플릿
├── routine          # 루틴 정의와 날짜별 실적
├── activity         # 실제 활동, 측정, 목표, 회고
├── finance          # 계좌, 금융 원장, 예산, 예정흐름
├── investment       # 종목, 관심종목, 거래, 보유현황
├── market           # 가격 공급자 어댑터, 시세 캐시, 환율
├── dashboard        # 도메인별 읽기 전용 집계
├── notification     # 인앱/푸시 알림 정책
├── integration      # 외부 OAuth, CSV import/export
└── automation       # 도메인 이벤트 소비, 규칙/실행 이력
```

각 모듈 내 권장 구성:

```text
calendar/
  api/             CalendarController, DTO
  application/     CalendarCommandService, QueryService
  domain/          Event, RecurrenceRule, Events
  infrastructure/  JPA Repository, ExternalCalendarAdapter
```

- `Controller`는 입력 검증과 서비스 호출만 담당.
- `Application Service`는 유스케이스·트랜잭션 경계.
- `Domain`은 핵심 불변식과 정책, 외부 SDK 직접 의존 금지.
- `Infrastructure`는 DB/HTTP/스케줄러/외부 제공처와 통신.
- 통합 홈은 다른 모듈의 **공개된 QueryService**를 호출한다. 모듈 간 JPA 엔티티를 직접 수정하지 않는다.

### 14.2 핵심 명령 흐름

```mermaid
sequenceDiagram
    participant UI as Web
    participant API as Money API
    participant DB as PostgreSQL
    participant W as Outbox Worker
    participant N as Notification
    UI->>API: POST /money/transactions (Idempotency-Key)
    API->>DB: BEGIN; auth owner 확인
    API->>DB: 거래 + posting + 이벤트 저장
    API->>DB: CHECK 각 통화별 정합성; COMMIT
    API-->>UI: 201 Created (transactionId)
    W->>DB: 미처리 outbox 조회/잠금
    W->>N: 예산 임계치 판정 및 알림 발행
    W->>DB: 발송 이력/완료 기록
```

**재시도:** 앱이 타임아웃됐지만 서버 저장이 완료되었을 수 있다. 동일 사용자+요청키+요청 본문 해시로 이전 결과를 반환하고, 같은 키로 다른 내용을 제출하면 `409 CONFLICT` 처리한다.

### 14.3 내부 이벤트 목록

```text
calendar.event.created | calendar.event.updated | calendar.event.deleted
calendar.sync.failed | calendar.sync.recovered
task.created | task.completed | task.reopened | task.deleted
routine.occurrence.completed | routine.occurrence.skipped
activity.recorded | activity.corrected | goal.progress.changed
money.transaction.posted | money.transaction.reversed
money.budget.threshold_crossed | money.bill.due
market.quote.updated | investment.price_alert.triggered
notification.requested | notification.sent | notification.failed
```

이벤트에는 `eventId`, `eventType`, `aggregateId`, `userId`, `occurredAt`, `schemaVersion`, `payload`가 있어야 한다. 개인 민감 내용은 최소화하고 외부 이벤트 로그에 인증 토큰/거래 전체 내용 기록 금지.

### 14.4 이벤트 정합성 정책

- 단일 DB 트랜잭션 안에서 원본 변경 + outbox 기록.
- 워커는 **at-least-once** 배달을 전제로 설계. exactly-once 전송 보장이라는 표현은 사용하지 않는다.
- 이벤트 소비자마다 `(consumer_name, event_id)` 유니크 인덱스로 중복 실행 차단.
- 실패 시 지수 백오프와 최대 재시도 횟수, 초과 시 dead-letter 상태.
- 외부 알림 전송은 네트워크 실패 시 실제 발송 여부가 불명확할 수 있어 전달 키/제공자 멱등성을 점검한다.
- 투자 시세 갱신 이벤트는 가격이 변화하지 않은 경우 불필요한 집계/알림을 억제.

### 14.5 시간 처리 정책

- API 응답 시각은 ISO 8601 오프셋 또는 UTC `Z`로 표현.
- 일정의 지역 날짜(`LocalDate`)와 절대 시각(`Instant`)을 서로 다른 타입으로 보존.
- `TODAY` 집계 기준: 사용자가 설정한 IANA 타임존의 자정부터 다음 자정 직전까지.
- 서머타임(DST)이 있는 국가에서는 1일을 무조건 86,400초로 취급하면 안 된다.
- 날짜별 반복은 `LocalDate + ZoneId`로 계산하고, 존재하지 않는 시각/중복 시각 처리 방침을 명시.
- 서버 배치가 실행되지 않아도 조회 시 미생성 반복 인스턴스를 안전하게 생성할 수 있도록 한다.

## 15. 데이터 모델과 관계

### 15.1 식별 및 공통 컬럼 정책

- PK: UUID 또는 UUIDv7 기반 ID. PostgreSQL 버전의 생성 기능 지원을 확인하고 앱에서 생성하는 방식도 허용.
- 사용자 소유 데이터에는 `user_id NOT NULL`; 소유자를 API 요청 body에서 선택하도록 하지 않는다.
- 소프트 삭제 대상은 `deleted_at`; 확정된 원장 금융 거래는 원본을 계속 `POSTED`로 보존하고, `reversed_of`로 원본을 참조하는 **역거래를 새로 `POSTED`**로 기록한다. 화면의 취소 여부는 역거래 존재로 파생 계산한다.
- `created_at`, `updated_at`, `version`(낙관적 잠금)을 변경 가능한 레코드에 적용.
- 사용자 삭제 시 도메인 데이터 삭제/익명화 및 백업 보존 정책을 별도로 정의.
- 금액 = 통화 + BigDecimal. 수익률/퍼센트 = 정밀 decimal. 비밀 토큰 = 암호화 컬럼.

### 15.2 필수 테이블 목록

| 테이블 | 중요한 필드 | PK/유니크·인덱스 포인트 |
|---|---|---|
| `app_user` | id, email, display_name, timezone, base_currency, status | `email_normalized` 유니크 |
| `user_setting` | user_id, dashboard_layout, quiet_hours, locale | user_id 유니크 |
| `auth_local_credential` | user_id, password_hash, password_changed_at, failed_attempts/locked_until(필요 시) | user_id PK/FK, 해시는 인증정보 전용 테이블 분리 |
| `auth_identity` | user_id, provider, provider_subject | `(provider, provider_subject)` 유니크 — 외부 로그인 도입 시 |
| `auth_security_event` | user_id(nullable), event_type, occurred_at, ip_hash(optional), request_id | 이벤트/기간별 보안 감사(비밀·원문 IP 최소화) |
| `email_verification_token`(후속) | user_id, token_hash, expires_at, consumed_at | 단일 사용·만료·재전송 제한 |
| `password_reset_token`(후속) | user_id, token_hash, expires_at, consumed_at | 단일 사용·만료·기존 세션 무효화 |
| `external_connection` | user_id, provider, encrypted_tokens, scopes, expires_at, status | 제공자별 연결 키 |
| `calendar` | user_id, name, color, source, external_id | `(user_id,source,external_id)` 조건 유니크 |
| `calendar_event` | user_id, calendar_id, start/end, all_day, timezone, rrule, external_id | `(user_id,start_at)` + 외부키 |
| `recurrence_exception` | master_id, recurrence_original_at, override_id, cancelled | `(master_id,recurrence_original_at)` |
| `task_project` | user_id, title, archived | `(user_id,title)` 선택 유니크 |
| `task` | user_id, title, status, due_date/at, priority, completed_at, version | `(user_id,status,due_at)` |
| `task_template` | user_id, rrule, title, start_date, enabled | 활성 템플릿 인덱스 |
| `routine` | user_id, rrule/weekdays, category_id, target_value/unit | 활성 목록 인덱스 |
| `routine_occurrence` | user_id, routine_id, date, target_snapshot, status | `(routine_id,date)` 유니크 |
| `activity_category` | user_id, name, unit | 사용자별 분류명 |
| `activity_log` | user_id, category_id, task_id, duration, quantity, started_at | `(user_id,started_at)` |
| `goal` | user_id, measurement_type, target, unit, period, source_policy | `(user_id,status,end_date)` |
| `daily_review` | user_id, local_date, good, improve, tomorrow | `(user_id,local_date)` 유니크 |
| `money_account` | user_id, name, type, currency, opening_balance, active | `(user_id,type)` |
| `money_category` | user_id, name, category_type, parent_id | 사용자별 카테고리 인덱스 |
| `money_transaction` | user_id, type, occurred_at, status, memo, external_ref, reversed_of | `(user_id,occurred_at)`, idempotency |
| `money_posting` | transaction_id, account_id, category_id, signed_amount, currency | `(transaction_id,account_id)` |
| `budget` | user_id, category_id, period_start/end, currency, limit_amount | `(user_id,category_id,period_start,period_end)` |
| `planned_cashflow` | user_id, account_id, category_id, amount, due_date, status, recurrence | `(user_id,due_date)` |
| `fx_rate` | base_currency, quote_currency, rate, quoted_at, provider | `(base,quote,quoted_at,provider)` |
| `instrument` | market, symbol, currency, name, asset_type | `(market,symbol)` 식별 |
| `market_quote` | instrument_id, provider, quoted_at, received_at, quote_type, price | `(instrument_id,provider,quoted_at)` |
| `watchlist` | user_id, name | `(user_id,name)` |
| `watchlist_item` | watchlist_id, instrument_id, sort_order | `(watchlist_id,instrument_id)` |
| `investment_account` | user_id, name, base_currency, money_account_id | 사용자별 계좌 |
| `investment_trade` | user_id, account_id, instrument_id, side, quantity, price, fees, taxes, traded_at | `(user_id,traded_at)` |
| `investment_lot` | account_id, instrument_id, remaining_qty, remaining_cost | 취득원가 정책 적용 |
| `price_alert` | user_id, instrument_id, condition, threshold, state, cooldown_until | `(user_id,enabled)` |
| `notification` | user_id, event_type, payload, status, read_at | `(user_id,created_at)` |
| `notification_delivery` | notification_id, channel, provider_ref, status, attempt_count | `(notification_id,channel)` |
| `idempotency_record` | user_id, scope, request_key, request_hash, response_status/body, expires_at | `(user_id,scope,request_key)` 유니크 |
| `outbox_event` | id, type, aggregate_id, user_id, payload, status, attempts | `(status,next_attempt_at)` |
| `processed_event` | consumer_name, event_id, processed_at | `(consumer_name,event_id)` 유니크 |
| `audit_log` | user_id, entity_type, entity_id, operation, actor, at, metadata | `(user_id,at)` |

> 구현 시 M0에 해당하는 테이블부터 작성한다. 모든 테이블을 첫 배포에 생성할 필요는 없다. 각 테이블의 구체 컬럼 타입은 Flyway 마이그레이션과 ORM 모델을 **단일 기준**으로 유지한다.

### 15.3 주요 관계(축약 ERD)

```mermaid
erDiagram
    APP_USER ||--o| AUTH_LOCAL_CREDENTIAL : authenticates
    APP_USER ||--o{ AUTH_SECURITY_EVENT : audit
    APP_USER ||--o{ CALENDAR : owns
    APP_USER ||--o{ TASK : owns
    APP_USER ||--o{ ROUTINE : owns
    APP_USER ||--o{ ACTIVITY_LOG : records
    APP_USER ||--o{ MONEY_ACCOUNT : owns
    APP_USER ||--o{ MONEY_TRANSACTION : records
    APP_USER ||--o{ INVESTMENT_TRADE : records
    APP_USER ||--o{ WATCHLIST : owns
    CALENDAR ||--o{ CALENDAR_EVENT : contains
    ROUTINE ||--o{ ROUTINE_OCCURRENCE : generates
    TASK |o--o{ ACTIVITY_LOG : relates_to
    MONEY_TRANSACTION ||--|{ MONEY_POSTING : contains
    MONEY_ACCOUNT ||--o{ MONEY_POSTING : account_posting
    WATCHLIST ||--o{ WATCHLIST_ITEM : contains
    INSTRUMENT ||--o{ WATCHLIST_ITEM : appears_in
    INSTRUMENT ||--o{ MARKET_QUOTE : quoted_as
    INSTRUMENT ||--o{ INVESTMENT_TRADE : traded_as
    APP_USER ||--o{ NOTIFICATION : receives
```

### 15.4 테이블 간 권한/정합성

- `money_posting.account_id`가 가리키는 계좌의 사용자와 거래의 사용자가 같아야 함.
- `watchlist_item.instrument_id`는 공통 종목 마스터를 참조해도 소유한 `watchlist`는 반드시 사용자별 격리.
- `activity_log.task_id` 참조는 같은 사용자 간 연결만 허용.
- 읽기 조회 시도 단독 레코드 ID만으로 접근하지 않고 `WHERE id=? AND user_id=?` 조건을 기본으로 한다.
- 권한 검사 보완책으로 PostgreSQL RLS 도입을 검토하되, **RLS만 믿고 서비스 권한검사를 생략하지 않는다**.

## 16. 핵심 SQL 마이그레이션 예시

아래는 **PostgreSQL 기준 설계 예시**로, 모든 도메인 테이블을 한 번에 생성하는 완성 마이그레이션이 아니다. PK 생성·인덱스·검증 트리거·FK 삭제 정책은 실제 구현 시 확정한다.

```sql
-- V1__identity_and_tasks.sql
CREATE TABLE app_user (
  id UUID PRIMARY KEY,
  email_normalized VARCHAR(320) NOT NULL UNIQUE,
  display_name VARCHAR(80) NOT NULL,
  timezone VARCHAR(80) NOT NULL DEFAULT 'Asia/Seoul',
  base_currency CHAR(3) NOT NULL DEFAULT 'KRW',
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE task (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  title VARCHAR(200) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'TODO',
  priority VARCHAR(2) NOT NULL DEFAULT 'P2',
  due_date DATE,
  due_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  estimated_minutes INTEGER CHECK (estimated_minutes IS NULL OR estimated_minutes >= 0),
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT ck_task_status CHECK (status IN ('TODO','IN_PROGRESS','DONE','CANCELED')),
  CONSTRAINT ck_task_priority CHECK (priority IN ('P0','P1','P2','P3')),
  CONSTRAINT ck_task_title CHECK (LENGTH(TRIM(title)) > 0),
  CONSTRAINT ck_task_done CHECK (
    (status = 'DONE' AND completed_at IS NOT NULL)
    OR (status <> 'DONE' AND completed_at IS NULL)
  )
);
CREATE INDEX idx_task_user_status_due ON task(user_id, status, due_date)
  WHERE deleted_at IS NULL;
```

```sql
-- V2__finance_ledger.sql
CREATE TABLE money_account (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  name VARCHAR(100) NOT NULL,
  kind VARCHAR(30) NOT NULL,
  currency CHAR(3) NOT NULL,
  opening_balance NUMERIC(20,6) NOT NULL DEFAULT 0,
  is_system BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_money_account_kind CHECK (
    kind IN ('ASSET','LIABILITY','EXPENSE','INCOME','EQUITY')
  ),
  UNIQUE (id, user_id)
);

CREATE TABLE money_transaction (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  tx_type VARCHAR(30) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'POSTED',
  occurred_at TIMESTAMPTZ NOT NULL,
  memo VARCHAR(500),
  reversed_of UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (id, user_id),
  FOREIGN KEY (reversed_of, user_id) REFERENCES money_transaction(id, user_id),
  CONSTRAINT ck_money_tx_status CHECK (status IN ('PENDING','POSTED'))
);
CREATE UNIQUE INDEX ux_money_tx_reversal ON money_transaction(reversed_of)
  WHERE reversed_of IS NOT NULL;

CREATE TABLE money_posting (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL,
  transaction_id UUID NOT NULL,
  account_id UUID NOT NULL,
  signed_amount NUMERIC(20,6) NOT NULL,
  currency CHAR(3) NOT NULL,
  category_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  FOREIGN KEY (transaction_id, user_id)
    REFERENCES money_transaction(id, user_id),
  FOREIGN KEY (account_id, user_id)
    REFERENCES money_account(id, user_id),
  CONSTRAINT ck_posting_nonzero CHECK (signed_amount <> 0)
);
CREATE INDEX idx_money_tx_user_time ON money_transaction(user_id, occurred_at DESC);
CREATE INDEX idx_money_posting_account ON money_posting(user_id, account_id);

-- 중요: posting 합계 0 여부는 INSERT 1행마다가 아닌
-- 모든 posting 저장 후 커밋 이전에 검증해야 함.
-- 초안에서는 서비스 트랜잭션에서 통화별 합을 검증하고,
-- 운영 단계에서는 DEFERRABLE constraint trigger 등 DB 레벨 보호 추가.
-- 'opening_balance'는 최초 기준값이며 이전 데이터를 중복 입력하지 말 것.
```

### 16.1 원장 계산 예제

확정된 거래(`status='POSTED'`)만 합산해야 하므로, **아래처럼 확정 거래를 내부 JOIN에서 먼저 거른 뒤 외부 JOIN**을 사용한다.

```sql
SELECT a.id,
       a.opening_balance + COALESCE(x.posted_delta, 0) AS ledger_balance
FROM money_account a
LEFT JOIN (
  SELECT p.account_id, SUM(p.signed_amount) AS posted_delta
  FROM money_posting p
  JOIN money_transaction t ON t.id = p.transaction_id
  WHERE t.status = 'POSTED'
  GROUP BY p.account_id
) x ON x.account_id = a.id
WHERE a.id = :account_id AND a.user_id = :user_id;
```

`opening_balance`를 가져올 때도 반드시 사용자 소유 계좌인지 먼저 확인하며, 통화가 다른 거래는 같은 계좌에 무분별하게 기록하지 못하게 한다. **부채 계좌는 signed balance를 음수로 관리**한다. 최초 부채는 음수, `현재 순자산 = 자산 잔액의 합 + 음수 부채 잔액의 합`.

### 16.2 DB 제약 테스트 (필수)

- posting이 1개뿐인 거래는 확정할 수 없음.
- 같은 통화에 대한 각 posting 합이 0이 아니면 확정 거절.
- 다른 사용자 소유 계좌와 거래 연결 불가(FK 또는 서비스 검증).
- `POSTED` 거래는 임의 수정/삭제/상태변경 금지; 정정은 역거래+신규 거래. 역거래 생성 후 원거래도 합계에 남아 있어야 결과가 정확히 상쇄된다.
- 동일 멱등키로 재전송 시 잔액이 2번 증가하지 않음.
- 임시/대기 거래가 계좌 `ledger_balance`에 포함되지 않음.
- 카드대금 결제 시 비용 집계는 늘지 않음.

## 17. REST API 설계

### 17.1 공통 규약

- Base path: `/api/v1`.
- JSON, UTF-8, 날짜/시간 ISO 8601, 금액/가격/수량은 오차 회피를 위해 **문자열 십진수** 권장.
- 목록 API: `cursor`, `size`, `sort`; 반복 조회 결과 안정 정렬.
- 페이지 크기 상한(예: 100건), 필터·정렬 허용 필드 whitelist.
- 모든 개인용 리소스는 인증된 사용자 ID로 조회; 임의 `userId` body 입력 신뢰하지 않음.
- 생성에는 `Idempotency-Key` 지원(특히 금융 거래/외부 동기화).
- 수정은 `If-Match`/버전 비교 또는 요청 `version`으로 동시 편집 충돌 처리.
- 응답에는 서버 `requestId`를 남겨 로그 추적.

### 17.2 핵심 API 라우트

| HTTP | 경로 | 기능 | 단계 |
|---|---|---|---|
| GET | `/auth/csrf` | 쿠키 세션 기반 CSRF 토큰 초기화(읽기 전용) | M0 |
| POST | `/auth/signup` | 이메일·비밀번호 회원가입 | M0 |
| POST | `/auth/login` | 이메일·비밀번호 로그인 및 세션 회전 | M0 |
| POST | `/auth/logout` | 현재 세션 로그아웃·쿠키 무효화 | M0 |
| GET | `/auth/session` | 로그인 여부·온보딩 상태(민감정보 제외) | M0 |
| POST | `/auth/password/forgot` | 이메일 재설정 링크 발송(항상 동일한 외부 응답) | M1 |
| POST | `/auth/password/reset` | 단회용 토큰으로 비밀번호 재설정 | M1 |
| POST | `/auth/email/verify` | 이메일 검증 토큰 확정(공개 가입 전 필수) | M1/공개 가입 시 |
| POST | `/auth/sessions/revoke-all` | 전체 기기 세션 종료(재인증 필요) | M1 |
| GET | `/users/me` | 로그인 정보/시간대/통화 | M0 |
| PATCH | `/users/me/settings` | 사용자 설정 | M0 |
| GET | `/dashboard/today?date=YYYY-MM-DD` | 오늘 종합 홈 | M0 |
| GET | `/dashboard/summary?period=week` | 주간 요약 | M1 |
| GET | `/calendar/events?from=&to=` | 날짜 범위 일정 | M0 |
| POST | `/calendar/events` | 일정 생성 | M0 |
| PATCH | `/calendar/events/{id}` | 일정 수정 | M0 |
| DELETE | `/calendar/events/{id}` | 일정 삭제/취소 | M0 |
| POST | `/calendar/connections/google` | Google 연결 시작(별도 OAuth 흐름) | M1 |
| POST | `/calendar/connections/{id}/sync` | 수동 동기화 요청 | M1 |
| GET | `/tasks?status=&date=&cursor=` | 할 일 목록 | M0 |
| POST | `/tasks` | 할 일 추가 | M0 |
| PATCH | `/tasks/{id}` | 할 일 수정 | M0 |
| POST | `/tasks/{id}/complete` | 할 일 완료 | M0 |
| POST | `/tasks/{id}/reopen` | 완료 취소 | M0 |
| GET | `/routines?active=true` | 루틴 정의 조회 | M0 |
| POST | `/routines` | 루틴 정의 생성 | M0 |
| GET | `/routines/occurrences?date=` | 날짜별 루틴 발생 | M0 |
| POST | `/routines/occurrences/{id}/complete` | 루틴 달성 | M0 |
| POST | `/activities` | 실제 활동 기록 | M0 |
| GET | `/activities/summary?from=&to=&unit=` | 활동 집계 | M0 |
| GET | `/goals` | 목표 목록 | M1 |
| POST | `/goals` | 목표 생성 | M1 |
| PUT | `/reviews/daily/{date}` | 일일 회고 저장 | M0 |
| GET | `/money/accounts` | 계좌/잔액 | M0 |
| POST | `/money/accounts` | 계좌 생성 | M0 |
| GET | `/money/transactions?from=&to=` | 확정 거래내역 | M0 |
| POST | `/money/transactions` | 수입/지출 거래 | M0 |
| POST | `/money/transfers` | 계좌 간 이체 | M0 |
| POST | `/money/transactions/{id}/reverse` | 거래 취소/정정 | M1 |
| GET | `/money/budgets?period=YYYY-MM` | 월간 예산/실적 | M0 |
| PUT | `/money/budgets/{id}` | 한도 변경 | M0 |
| GET | `/money/reports/expense?from=&to=` | 기간별 소비 리포트 | M1 |
| GET | `/instruments/search?q=&market=` | 종목 검색 | M0 |
| GET | `/market/quotes?ids=` | 관심 가격 조회 | M0 |
| GET | `/invest/watchlists` | 관심목록 | M0 |
| POST | `/invest/watchlists/{id}/items` | 관심종목 추가 | M0 |
| DELETE | `/invest/watchlists/{id}/items/{instrumentId}` | 관심종목 제거 | M0 |
| GET | `/invest/portfolio` | 보유현황·추정 평가액 | M1 |
| POST | `/invest/trades` | 수동 매수/매도 기록 | M1 |
| POST | `/invest/price-alerts` | 가격 알림 조건 등록 | M1 |
| GET | `/notifications` | 알림 내역 | M0 |
| PATCH | `/notifications/{id}/read` | 알림 읽음 | M0 |
| GET | `/search?q=&types=` | 통합 검색 | M1 |
| POST | `/exports` | 내 데이터 내보내기 작업 | M1 |
| POST | `/imports/transactions/preview` | 거래 CSV 사전 검증 | M2 |

### 17.3 DTO 예제 — 할 일 생성

```http
POST /api/v1/tasks
Content-Type: application/json
Idempotency-Key: 5f9b3...   
```

```json
{
  "title": "Java 공부 1시간",
  "priority": "P1",
  "dueDate": "2026-11-02",
  "estimatedMinutes": 60,
  "activityCategoryId": "1b5b5907-48be-465f-a7af-3e089f55c901"
}
```

```json
{
  "id": "159f1e15-d58c-4ef0-a6ac-8c30ecab3f7f",
  "title": "Java 공부 1시간",
  "status": "TODO",
  "priority": "P1",
  "dueDate": "2026-11-02",
  "completedAt": null,
  "version": 0
}
```

### 17.4 DTO 예제 — 지출 등록

```http
POST /api/v1/money/transactions
Content-Type: application/json
Idempotency-Key: 2dc3e83c-97d6-4db5-9285-e937a8ac24b9
```

```json
{
  "type": "EXPENSE",
  "sourceAccountId": "0fa7251f-6583-4bca-b4da-3d085221a768",
  "occurredAt": "2026-11-01T12:30:00+09:00",
  "currency": "KRW",
  "totalAmount": "9500",
  "allocations": [
    { "categoryId": "c1fa9fd8-610a-4f4c-86e2-0f48a30018d3", "amount": "9500" }
  ],
  "memo": "점심"
}
```

**서버 검증:** 금액 > 0, 분할합계=총액, 계좌 통화 일치, 카테고리 사용자 소유, 멱등키 중복, posting 합 0. 저장 성공 후 거래 ID·posting·잔액/예산 집계 버전 응답.

### 17.5 DTO 예제 — 오늘 대시보드

```json
{
  "date": "2026-11-01",
  "timezone": "Asia/Seoul",
  "generatedAt": "2026-11-01T08:00:02+09:00",
  "tasks": { "total": 6, "completed": 2, "overdue": 1 },
  "events": [{ "id": "event-1", "title": "운동", "startsAt": "2026-11-01T18:30:00+09:00" }],
  "money": {
    "currency": "KRW",
    "todayExpense": "18500",
    "budget": { "limit": "300000", "spent": "186000", "remaining": "114000" }
  },
  "routines": { "scheduled": 3, "completed": 1 },
  "watchlist": {
    "items": [],
    "state": "NOT_CONFIGURED",
    "quotesAsOf": null
  },
  "warnings": ["TASK_OVERDUE"]
}
```

위 화면 값과 ID는 **개발용 샘플**이다. 주식 API 미설정/시세 장애/관심목록 비어있음은 `state`로 구분한다.

### 17.6 API 에러 표준

```json
{
  "type": "https://my-today.example/errors/conflict",
  "title": "Conflict",
  "status": 409,
  "detail": "같은 요청 키로 다른 내용이 제출되었습니다.",
  "code": "IDEMPOTENCY_KEY_REUSED",
  "requestId": "req-..."
}
```

| 코드 | HTTP | 의미 |
|---|---|---|
| `VALIDATION_ERROR` | 400 | 입력형식/값 오류 |
| `UNAUTHENTICATED` | 401 | 로그인 필요 |
| `FORBIDDEN` | 403 | 접근 권한 없음 |
| `NOT_FOUND` | 404 | 자신의 리소스가 아님/존재하지 않음(노출 방지) |
| `VERSION_CONFLICT` | 409 | 다른 기기에서 먼저 수정됨 |
| `IDEMPOTENCY_KEY_REUSED` | 409 | 동일 키 다른 본문 |
| `RATE_LIMITED` | 429 | 요청량 제한 |
| `EXTERNAL_PROVIDER_UNAVAILABLE` | 503 | 외부 서비스 연결 장애 |

### 17.7 서버 응답 전략

- 쓰기: PostgreSQL COMMIT 후 성공 응답; 도메인 이벤트 전달은 비동기.
- 대시보드: 각 카드의 새로고침 시간과 상태를 제공. 하나의 공급자 실패로 전체 500 처리 지양.
- 시세 조회: 서버 캐시+요청 병합; 프런트 직접 제3자 시세 API 키 사용 금지.
- 개인정보/금융 API: 인증 쿠키, CSRF 정책, origin 검사, 레이트 리밋.

## 18. 외부 서비스 연동 및 제약

### 18.1 연동 우선순위

| 연동 후보 | 활용도 | 시작 시점 | 확인할 사항 |
|---|---|---|---|
| Google Calendar API | 기존 일정을 MY TODAY에 가져오기 | M1 | OAuth scope·동기화·토큰·정책 |
| KIS 한국투자증권 Open API | **본인 투자 목적** 국내외 종목 시세 | 개인용 검증 | 시세 재배포 제한, 계정/키·호출량 |
| Twelve Data 등 시세 제공자 | 지원 범위에 따른 미국 주가/환율 등 | 검토 | 상품별 라이선스·저장·상업적 사용 |
| 금융감독원 OpenDART | 국내 상장기업 공시/재무정보 | M2 | 공시는 주식 시세 대체재가 아님 |
| 공공 환율 데이터 소스 | 기준 통화 환산 | M1 | 데이터 사용권·갱신일·휴일 |
| 브라우저 알림/Push | 알림 표시 | M0/M1 | 브라우저 지원·사용자 동의 |
| 계좌/카드 데이터 연동 | 거래 수집 자동화 | M2 이후 별도 조사 | 정식 제휴·마이데이터/오픈뱅킹 권한 필요 가능 |

### 18.2 Google Calendar 연동 상세

**연동 흐름**

1. 사용자가 Google Calendar 연결 버튼 클릭.
2. 백엔드에서 OAuth authorization code + PKCE(해당 클라이언트에 적합한 패턴)와 `state`/nonce 보호를 적용해 인증 흐름 시작.
3. 최소 필요 스코프로 **읽기 전용**부터 동의받는다. 사용자의 외부 캘린더 쓰기는 M2 또는 별도 동의 이후.
4. 서버는 받은 refresh token 등 필요한 토큰을 암호화해서 저장하고 브라우저에 전달하지 않는다.
5. 첫 연결 시 Calendar 목록과 Events 전체 동기화, `nextSyncToken` 안전 저장.
6. 이후 `syncToken`을 이용한 증분 동기화. 삭제된 외부 이벤트도 반영.
7. 동기화 페이지가 여러 개이면 모든 페이지를 처리한 다음 마지막의 새 `nextSyncToken` 커밋.
8. `syncToken` 무효화(예: 410) 시 **전체 재동기화**가 가능하게 구현.
9. Webhook push는 변경 신호일 뿐 상세 이벤트 데이터 자체가 아니다. 신호 수신 후 증분 조회 수행.
10. Watch 채널 갱신/만료, 연결 해제 및 외부 토큰 폐기, 사용자의 접근 권한 철회 처리.

**동기화 데이터 구조**

```text
external_connection
  provider, user_id, encrypted_refresh_token, scopes, status
calendar_sync_cursor
  connection_id, external_calendar_id, next_sync_token, last_sync_at
calendar_watch_channel
  connection_id, channel_id, resource_id, secret_hash, expires_at
calendar_event
  source, external_calendar_id, external_event_id, external_revision/etag
```

**충돌 처리:** M1에서는 외부 캘린더 `READ_ONLY`. 사용자가 MY TODAY에서 만든 내부 일정만 편집. M2 양방향 전환 시 수정 시각·etag·충돌 정책을 명확히 설계한다.

**공개 서비스 정책:** Google Calendar 읽기 스코프 등은 민감한 범주일 수 있으며, 공개 서비스 OAuth 검증/동의화면/개인정보처리방침 요건이 발생할 수 있다. 예외 및 필요 절차는 Google 공식 문서에서 확인해야 한다.

### 18.3 주식 시세 공급자 추상화

```java
public interface MarketDataProvider {
    QuoteResult getQuote(InstrumentRef instrument);
    List<QuoteResult> getQuotes(List<InstrumentRef> instruments);
    ProviderCapabilities capabilities();
}

public record QuoteResult(
    String market,
    String symbol,
    BigDecimal price,
    Currency currency,
    Instant quotedAt,
    Instant receivedAt,
    QuoteKind kind,
    String providerCode
) {}
```

- `KisMarketDataProvider`, `TwelveDataMarketDataProvider`, `MockMarketDataProvider`처럼 교체 가능.
- `MarketDataService`는 공급자별 레이트 제한·토큰·지수 백오프·회로 차단기·조회 캐시 관리.
- Provider capabilities에 `supportedMarkets`, `realtimeAllowed`, `redistributionAllowed`, `historicalAllowed`, `persistAllowed`, `licenseScope` 등을 기록.
- 권한이 없는 데이터는 **캐싱·다른 사용자 전달·공개 노출하지 않는다**. 단순 provider interface만으로 라이선스 문제가 해결되지는 않는다.
- 시세 공급자 장애 때 수동 보유종목/투자 기록은 열람 가능해야 한다.

### 18.4 한국투자증권 API 확인 사항 (2026-11-01 기준)

- 공식 개발자센터에는 국내외 주식 REST/웹소켓 API가 안내되어 있다.
- 개인 고객 시세정보는 본인의 자산 투자 목적에 한한 이용이라는 제한과 **제3자 시세 제공 금지** 문구가 있다.
- 호출량/제한 정책이 바뀔 수 있으므로 무제한·무료 실시간으로 가정하지 않는다.
- 2026-11-11 이후 종료 예정이라고 공지된 국내주식 **재무분석 API 7종**(대차대조표, 손익계산서, 재무비율, 수익성/주요/안정성/성장성 비율)은 의존 대상으로 채택하지 않는다. 이는 공식 발표에 따른 예정 사항이며 구현 시 최신 공지를 재확인한다.
- **실제 매매 API가 존재하더라도 이 앱의 주문 기능은 개발 범위에서 제외**한다.

### 18.5 Twelve Data 등 기타 시세 API

- 무료 체험/개인용 데이터 접근은 제공 범위·호출 크레딧·시장별 권한에 따라 제한됨.
- 공식 요금표에 현재 무료 Basic 플랜이 있지만 무료라는 사실이 **상업적 화면 재배포 허용**을 의미하지 않는다.
- 제공처의 `internal display`, `non-display`, `redistribution`, `storage` 용어는 서로 다른 허가 범위로 이해해야 한다.
- 구체 가격과 요청 횟수는 가입 계정, 상품, 시점에 따라 변경될 수 있으므로 실제 구현 시 공식 대시보드/계약 확인.

### 18.6 사용자 데이터 가져오기

**1차 권장:** CSV 가져오기만 제한적으로 지원하며, 자동 수집보다 안전성과 구현 비용을 통제한다.

1. 파일 업로드 → MIME/파일크기 제한 → 바이러스/악성 수식(CSV formula injection) 검사 → 인코딩 지정.
2. 사용자에게 컬럼 맵핑(날짜, 금액, 구분, 카테고리, 메모, 계좌)을 보여준다.
3. 드라이런(preview)으로 신규/중복 후보/오류를 확인한다.
4. 승인 후 트랜잭션 단위 또는 배치로 저장, 실패 건을 보고한다.
5. 같은 파일/같은 은행 거래를 다시 가져올 경우 `source + external_ref` 또는 지문(hash)으로 중복 탐지.
6. 파일 원본은 기본 삭제; 보존 시 사유·기간·암호화 정책 필요.

**금지:** 사용자 은행 로그인 정보·카드 비밀번호 요구, 무단 스크래핑, SMS 권한을 통한 무조건적 수집.

## 19. 인증·보안·개인정보·백업

### 19.1 인증·인가 정책

- **M0 확정 방향:** 무료 PWA를 위한 이메일/비밀번호 로그인 + Spring Security 서버 세션. 제품 계정 식별은 `app_user.id`로 통일. 인증 흐름 상세는 29장 참조.
- **가입 정책:** 초기 개인용 베타는 초대/사전 생성 계정으로 제한 가능. 누구나 가입 가능한 공개 서비스 전환 전에는 실제 이메일 소유 확인·비밀번호 재설정 발송 경로를 반드시 구현한다.
- Google/OIDC 소셜 로그인은 M1 선택 기능이며 Google Calendar 계정 연동과는 구분한다.
- Spring Security 서버 세션+`HttpOnly`, `Secure`, `SameSite` 쿠키 우선(동일 사이트 웹 기준). OAuth 이후 세션 고정 보호.
- 웹의 상태 변경 요청은 로그인/가입/로그아웃을 포함해 CSRF 방어, CORS 허용 출처 화이트리스트, 로그인·로그아웃 정책. `Origin`/`Referer` 검증도 보완책으로 사용.
- 모바일 확장 시 OAuth Authorization Code + PKCE, 시스템 브라우저, OS secure storage를 사용하고 토큰은 웹 쿠키 모델을 그대로 복제하지 않는다.
- 민감 기능(내보내기·전체 삭제·연결 계정 변경)은 최근 로그인 재인증 검토.
- 관리자용 백도어와 개발자 공통 비밀번호 금지.

### 19.2 개인 데이터 분리

- 모든 읽기/쓰기 경로에서 서버 인증 사용자를 기준으로 필터링.
- 수동으로 타인 UUID를 넣어도 `404/403` 처리가 일관되어야 한다.
- 다른 사용자 소유 `accountId`, `categoryId`, `taskId`를 요청에 끼워 넣는 IDOR 테스트 수행.
- SQL Injection 방어: 바인딩 변수 사용, 정렬/필드 이름을 문자열 직접 조합하지 않음.
- XSS 방어: 메모/일정 제목을 HTML로 직접 렌더링하지 않음, Markdown 렌더러는 sanitize.

### 19.3 비밀정보 저장

- `.env`, OAuth client secret, 증권 API Key/Secret, DB 비밀번호, refresh token은 Git에 커밋 금지.
- 시크릿 저장소 또는 서버 환경변수 사용; 운영환경 로그/에러/백업/스크린샷 노출 금지.
- 외부 OAuth refresh token과 민감 메모는 저장 데이터 암호화(AES-GCM 등 인증 암호화), 키 회전 계획.
- 이메일/비밀번호 로그인 구현 시 평문·가역 암호화 저장을 금지하고 **Argon2id(운영 자원에 맞춰 튜닝) 또는 검증된 Spring PasswordEncoder**로 단방향 해시만 저장한다.
- HTTPS, HSTS, 보안 헤더, API 호출량 제한, IP/기기 이상 로그인 방어.

### 19.4 개인정보 최소화 및 권리

- 기능 제공에 불필요한 생년월일·정확한 위치·주민등록번호를 수집하지 않는다.
- 위치 기반 기능은 차후 선택 동의·권한 요청; 이 앱 초기 범위는 필요 없음.
- 사용자별 데이터 내보내기(JSON/CSV)와 삭제 요청 기능 구현.
- 외부 계정 연결 해제 시 토큰 폐기·동기화 중단 및 사본 삭제 정책 고지.
- 공개 서비스 전환 전에 개인정보처리방침/이용약관/위탁·국외 이전 여부/시세 사용권 검토.
- 앱 로깅·분석에 거래 메모나 보유종목 전체를 개인정보처럼 취급하여 최소 노출.

### 19.5 백업 및 복구

- PostgreSQL 정기 백업 + 암호화 + 원격/별도 매체 복사.
- 복구 가능한 백업인지 **실제 복원 테스트**를 수행한다.
- 기준 제안: 매일 백업, 변경량에 따른 추가 WAL 보관, 7일/30일/월별 보존 정책을 저장공간에 맞춰 확정.
- 백업 암호화 키는 백업과 다른 경로에 보관.
- 데이터 완전 삭제 요청이 백업 보존주기와 어떻게 연동되는지 정책 문서화.
- 장애 복구 목표(RPO/RTO)는 사용자 데이터 손실 허용도에 맞춰 정하고 실제 복구 시험 결과로 검증.

## 20. 성능·관측·운영·비용

### 20.1 초기 성능 목표(설계 목표, 실측값 아님)

| 항목 | 목표 | 검증 |
|---|---|---|
| 홈 조회 | 주요 데이터 수집 실패 없이 정상 응답, 서버 내부 p95 목표 500ms (외부 실시간 호출 제외) | 로드 테스트 |
| 할 일 생성/완료 | 일반 요청 p95 목표 300ms(서버 처리) | API 통합 테스트 |
| 가계부 거래 저장 | 중복 없이 원자적 커밋 | 동시성/트랜잭션 테스트 |
| 시세 | 캐시 우선, 외부 제공자 SLA 별도 | 제공자별 메트릭 |
| 검색 | 사용자별 대표 데이터에서 p95 700ms 목표 | 인덱스/실행계획 확인 |
| 가용성 | 개인용은 복구 가능성 우선, 공개 전 SLA 수립 | 상태 체크/알림 |

### 20.2 시세 API 비용 통제

```text
종목 A 실시간 조회 요청 10명/3초 → 서버는 공급자 호출을 가능한 경우 1번으로 병합
                         ↓
                   cache + request coalescing
                         ↓
                  라이선스 허용 범위 확인
```

단, **제3자 전달이 불허된 개인용 API 데이터를 여러 사용자에게 공유하는 최적화는 하지 않는다.** 캐시 공유/중계는 계약상 허용된 데이터에 한하여 적용.

- 사용자 화면에 가격이 보이지 않을 때 시세 갱신 빈도 낮춤.
- 관심종목 수 제한/배치 호출/TTL/시장 휴장 판정/오류 백오프.
- 실시간 구독은 필요한 종목만 하고 연결 종료 후 해제.
- API 키별 사용량/쿼터/오류를 메트릭으로 관측, 한도 초과 시 무한 재시도 금지.

### 20.3 운영 비용 카테고리

| 비용 항목 | 초기 절감 전략 | 실제 결제 전 확인 |
|---|---|---|
| 서버/VPS | 기존 소형 서버 또는 저가 VPS 1대 | 네트워크 안정성, HTTPS, 백업 경로 |
| DB | 동일 서버 PostgreSQL (개인용) | DB 백업·접근 통제 |
| 도메인/SSL | 기존 도메인 활용, Let's Encrypt | 도메인 갱신/인증서 자동갱신 |
| 시세 API | 수동 관심종목+허용된 저빈도 데이터로 시작 | 실시간 사용권·시세 재배포 비용 |
| 알림 | 인앱 우선, 허용된 무료 푸시 | iOS/Android 정책·사용량 |
| 스토리지 | 텍스트/수치 기반으로 소량 유지 | 사진 첨부·백업 확대 비용 |
| 생성형 AI | M0/M1 제외, 규칙 기반 계산 | 과금·개인정보·모델 버전 |
| 모바일 배포 | PWA 먼저 | 앱스토어 계정/심사·개발자 비용 |

**금액 견적을 확정하지 않는 이유:** 공급자 요금·전송량·이용권은 변할 수 있고, 공개 서비스와 개인용 사용권이 완전히 다르기 때문이다. 소프트웨어가 무료여도 데이터 제공권은 유료일 수 있다.

### 20.4 관측성

- 메트릭: API 응답 시간/5xx, DB pool 사용률, 배치 성공률, 외부 API 429·401·5xx, 시세 freshness, 캘린더 sync lag, 멱등 충돌, 알림 실패율.
- 로그: JSON 구조화, `requestId`, 도메인 이벤트 ID, 작업 ID 포함. 거래 세부 금액·토큰·메모는 기본 마스킹.
- 건강 확인: liveness와 readiness 분리. 외부 시세 API 장애가 서비스 전체 ready 실패로 이어지지 않게 구성.
- 실무 운영: 서버 재시작 후 미완료 outbox/배치가 이어서 처리되는지 확인.

## 21. 테스트와 수용 기준

### 21.1 도메인별 테스트 시나리오

| 테스트 ID | 전제 | 행동 | 기대 결과 |
|---|---|---|---|
| T-CAL-001 | 타임존 Asia/Seoul | 23:50 일정 입력 | 다음날로 임의 변경되지 않음 |
| T-CAL-002 | 매주 월요일 반복 일정 | 특정 1회만 취소 | 다른 반복 발생일 유지 |
| T-CAL-003 | Google 연결, 동기화 완료 | 같은 외부 이벤트 두 번 동기화 | 내부 이벤트 1개 유지 |
| T-CAL-004 | 유효하지 않은 syncToken | 증분 동기화 | 전체 재동기화 후 중복 없이 복구 |
| T-TASK-001 | 미완료 할 일 | 완료 요청 2회 | 1번만 상태 전환, 중복 활동 0건 |
| T-TASK-002 | 같은 할 일을 두 기기에서 수정 | version 불일치 쓰기 | 후행 요청 409/충돌 안내 |
| T-RUT-001 | 월~금 루틴 | 주말 날짜 조회 | 해당 루틴 발생 없음 |
| T-RUT-002 | 20분 목표로 2회 기록 후 40분으로 변경 | 이전 날짜 조회 | 기존 달성률 20분 기준 유지 |
| T-ACT-001 | 할 일 완료 후 활동 40분 기록 | 성장 차트 조회 | 40분만 1회 반영 |
| T-ACT-002 | 활동 수정 40→20분 | 성장 차트 조회 | 누적량 20분으로 재계산 |
| T-MNY-001 | 현금 100,000원 | 12,000원 식비 지출 | 잔액 88,000원, 지출 12,000원 |
| T-MNY-002 | A 계좌 100,000원, B 0원 | A→B 50,000원 이체 | 총 현금 100,000원 유지, 소비 0원 |
| T-MNY-003 | 카드 부채 12,000원 | 은행에서 카드대금 납부 | 현금 -12,000원, 부채 +12,000원, 소비 추가 0원 |
| T-MNY-004 | 월 카페예산 30,000원 | 지출 24,000원 | 사용률 80%, 임계 알림 1건 |
| T-MNY-005 | 같은 `Idempotency-Key` | 거래 POST 반복 | 1개 거래만 생성, 원장 1회 반영 |
| T-MNY-006 | 한 거래 posting 합 불일치 | 확정 시도 | 전체 롤백, 잔액 불변 |
| T-MNY-007 | 미확정 거래 존재 | 잔액 조회 | `PENDING`은 확정 잔액에 미반영 |
| T-MNY-008 | 환불 기록 | 원래 지출 연결 | 원거래 이력 보존, 월간 순지출 재계산 |
| T-INV-001 | 가격 공급자 장애 | 투자 홈 조회 | 마지막 가격+기준시각/오류 상태, 다른 홈 카드 정상 |
| T-INV-002 | 시세 데이터 지연 | 표시 | `DELAYED` 또는 `STALE` 명시 |
| T-INV-003 | 100달러 매수, 120달러 시세 | 평가 조회 | 평가손익 계산, 원화 환산은 환율 별도 |
| T-INV-004 | 휴장일/오래된 종가 | 가격 알림 평가 | 동일 알림 중복 발행 없음 |
| T-SEC-001 | 사용자 A 인증 | 사용자 B 계좌 UUID로 조회 | 데이터 비노출(404/403) |
| T-SEC-002 | OAuth 토큰 갱신 실패 | 캘린더 조회 | 연결 상태 경고, 내부 일정 조회 정상 |
| T-SEC-003 | XSS 메모 입력 | 홈/리포트 표시 | 스크립트 실행 없이 텍스트 처리 |
| T-OPS-001 | 저장 직후 네트워크 단절 | 동일 요청 재전송 | 중복 저장 없음 |
| T-OPS-002 | DB 백업 파일 | 격리된 테스트 DB 복원 | 데이터 정합성 및 앱 기동 검증 |
| T-PWA-001 | 네트워크 오프라인 | 저장 시도 | 입력 내용 유지, 실패/재시도 안내 |

### 21.2 단위 테스트 필수 정책

- `MoneyPostingBalancePolicyTest`: 한 통화의 posting 합계가 정확히 0인지.
- `CardPaymentRecognitionTest`: 카드 사용/청구/상환 시 지출 이중계산 없는지.
- `BudgetThresholdPolicyTest`: 정확히 80%, 100%에 걸쳤을 때 중복 알림 없는지.
- `RoutineOccurrencePolicyTest`: 요일·휴일·반복 정의 변경·DST·과거 snapshot.
- `TaskActivityLinkTest`: 완료 취소/다시 완료/활동 수정 시 중복 집계 없는지.
- `CostBasisCalculationTest`: 일부 매도·수수료·액면분할·0원가 안전 처리.
- `PriceAlertPolicyTest`: 임계 가격 도달/이탈/재무장/쿨다운·지연 데이터.
- `UserResourceAuthorizationTest`: 다른 사용자의 ID 입력 공격 방어.

### 21.3 통합 테스트 환경

- PostgreSQL 실제 DB를 Testcontainers로 띄워 JPA/SQL/Flyway 테스트.
- 외부 API는 WireMock/MockWebServer 등으로 인증 오류, 429, 타임아웃, 부분 응답, 빈 결과, 페이징 구현.
- 클럭은 `Clock` 주입으로 고정해 자정 경계·요일·장 마감·알림 시간 테스트.
- 계약 테스트로 FE/BE DTO 호환성 확인.
- Playwright E2E: 로그인 → 홈 → 할 일 완료 → 지출 추가 → 예산 반영 → 로그아웃.

### 21.4 데이터 품질 규칙

- 금융 거래 총액/분할 합계/원장 합계에 `Double`, `float` 사용 금지.
- 가계부 검색·집계는 **개인 타임존에 맞는 날짜 경계**에서 수행.
- 주식 평가액은 `quote.quotedAt`과 `fx_rate.quotedAt`을 각각 보여주고 동일 시점인 척 합치지 않는다.
- 외부 API 에러/데이터 없음과 실제 0값을 구분.
- 모든 사용자 입력 텍스트/파일명은 허용 크기 제한.
- 외부 데이터의 라이선스 제한이 강해지거나 공급 중단 시 앱 전체 다운 없이 기능 축소 동작.

### 21.5 릴리즈 DoD(완료 정의)

1. 기능별 Acceptance Criteria 충족 및 단위/통합 테스트 통과.
2. DB 스키마 변경은 Flyway migration, 재현 가능.
3. 최소 권한 검증·민감정보 마스킹 확인.
4. 시세/계좌 연동의 실패·토큰 만료·오래된 데이터 표시 확인.
5. 모바일 화면·키보드·접근성 기본 검사 통과.
6. API/README/운영 문서가 변경된 코드와 일치.
7. 복구 가능한 DB 백업이 있고 복원 시험을 통과.
8. 공개 서비스가 아닌 개인용인 경우 공개 가능성이 있는 금융 시세 데이터 외부 유출 차단.

## 22. 단계별 개발 계획

> 시간은 개인 개발 속도에 따라 달라지므로 임의의 완공 일정을 약속하지 않는다. 단계별 **구현 내용·산출물·통과 기준**으로 관리한다.

### Phase 0 — 프로젝트 부트스트랩

**구현할 것**

- 모노레포 폴더 생성, Gradle/Java 21/Spring Boot, Vue 3/Quasar/TypeScript 초기화.
- PostgreSQL Docker Compose, Flyway, 프로파일 local/test/prod 분리.
- GitHub Actions CI(백엔드 테스트, 프런트 빌드, 린트).
- 공통 에러 응답, 공통 시계/시간대, 로깅 requestId, OpenAPI 문서.
- **최초 화면(`/welcome`) → 로그인(`/login`)/회원가입(`/signup`) → 최초 설정(`/onboarding`) → 홈(`/today`)** 구현. 비인증 보호 라우트 접근 제한 및 `/auth/session` 연동.
- 이메일/비밀번호 회원 인증, CSRF·세션 보안·로그아웃·회원가입 유효성 검증. 공개 가입을 열기 전에 이메일 검증/재설정 발송 체계 마련.
- 사용자 데이터 격리 기본 구조 완성. PWA manifest/HTTPS/홈 화면 설치 기반 구성.

**산출물:** `apps/web`, `services/api`, `infra`, `docs`, 로컬 실행 매뉴얼, 첫 GitHub Actions 성공.

**완료 조건:** 새 PC에서 README 명령대로 실행 가능, DB 마이그레이션 성공, 회원가입/로그인→`/api/v1/users/me`→로그아웃 동작, 비로그인 홈/가계부 접근 차단, iPhone/Android 브라우저에서 로그인 화면 정상 표시.

### Phase 1 — 오늘/할 일/일정 최소 사용 제품

**구현할 것**

- 할 일 CRUD·상태/우선순위/날짜별 조회·낙관적 잠금.
- 내부 캘린더 일정 CRUD·월/주/일 뷰·하루종일 구분.
- 오늘 대시보드 일정/할 일 카드 및 빠른 입력.
- 반응형 홈/목록/상세/모달 디자인, 빈 상태·오류 상태.

**완료 조건:** 가입/로그인 후 `오늘 할 일 추가→완료`와 `내일 일정 등록→조회`가 웹에서 안정적으로 동작.

### Phase 2 — 실제 활동·루틴·성장

**구현할 것**

- 활동 분류/기록/시간·횟수 집계.
- 루틴 반복 정의와 날짜별 인스턴스/실적.
- 오늘의 계획-실행 연결, 활동 중복 집계 방지.
- 간단한 목표/일간 회고/주간 통계.

**완료 조건:** 계획 60분·실제 40분을 입력했을 때 실제 활동 40분만 성장 통계에 반영되고, 루틴 목표를 바꿔도 과거 결과 보존.

### Phase 3 — 가계부 정합성 구현

**구현할 것**

- 계좌/가상계정/카테고리/거래 원장 생성.
- 수입/지출/이체/카드 부채상환 유스케이스.
- 멱등 저장, 취소/정정(역거래), 정합성 테스트.
- 월간 예산·카테고리 통계·홈 지출 카드.

**완료 조건:** 21장 금융 핵심 테스트 전부 통과하고, 거래 등록→잔액→월간 예산이 일관됨.

### Phase 4 — 관심종목/시세 조회

**구현할 것**

- 종목 마스터/시장 식별, 관심목록.
- 허용된 공급자 1개 연동; Mock Provider 병행.
- 공급자 인증키 서버 보관, 호출 한도/캐시/장 상태/갱신시각.
- 투자 화면 종목/시세 카드 및 홈 위젯.

**완료 조건:** 허용된 사용권에서 관심종목 표시 가능하고, API 장애/휴장/데이터 지연을 정확히 표현.

### Phase 5 — 통합성과 안정성 강화

**구현할 것**

- 구독/고정비/예정 현금흐름·알림.
- Google Calendar 읽기 동기화.
- 인앱 알림/사용자 알림 설정, 외부 token 갱신/복구.
- PWA 설치 지원, 오프라인 읽기 캐시 범위 정의.
- 공통 검색·위젯 순서/숨김 설정.

**완료 조건:** 내 일정/할 일/가계부/주가가 한 홈에서 안정적으로 표시되고, 외부 연동 장애가 홈 전체를 막지 않음.

### Phase 6 — 투자 포트폴리오·리포트

**구현할 것**

- 투자 거래 수동 등록, 보유 원가/평가액, 수수료/세금.
- 가격 임계 알림과 데이터 신선도.
- 월간 순자산/지출/루틴·성장 인사이트.
- 내 데이터 CSV/JSON 내보내기.

**완료 조건:** 주식 평가액과 가계부 현금 계좌 사이의 이중집계가 없고, 환율 미확보 시 정확한 미집계 상태 표시.

### Phase 7 — 네이티브 iOS·Android 정식 배포(선택, 후순위)

**구현할 것(진행 결정 이후)**

- **1차 무료 PWA 출시에 이 단계는 필요하지 않다.** 아이폰 Safari·Android Chrome에서 홈 화면 설치, 사진 촬영/선택, 웹 기반 무료 OCR을 먼저 검증한다.
- 필요해지면 Quasar + Capacitor 네이티브 패키징·OS 카메라/인증·플랫폼 알림을 추가한다.
- 플랫폼별 서명/스토어 등록/심사 비용은 진행 시점에 재평가한다.
- 폰 해상도·접근성·입력 품질을 재검증하고, 공개 서비스 전환 시 데이터 라이선스/OAuth/개인정보 법률을 검토한다.

**완료 조건(진행 시):** PWA에서 사용하던 계정과 공통 API/데이터를 유지하며, 네이티브 앱에서 기본 시나리오가 동작한다.

## 23. GitHub 프로젝트 구성과 개발 규칙

### 23.1 목표 폴더 구조

```text
my-today/
├── README.md
├── .gitignore
├── .editorconfig
├── .github/
│   ├── workflows/
│   │   ├── ci-api.yml
│   │   ├── ci-web.yml
│   │   └── release.yml
│   ├── ISSUE_TEMPLATE/
│   └── PULL_REQUEST_TEMPLATE.md
├── apps/
│   └── web/
│       ├── src/
│       │   ├── boot/
│       │   ├── components/
│       │   ├── layouts/
│       │   ├── pages/
│       │   ├── features/
│       │   │   ├── today/ calendar/ tasks/ growth/
│       │   │   ├── money/ invest/ settings/
│       │   ├── composables/
│       │   ├── stores/
│       │   ├── api/
│       │   └── styles/
│       ├── public/
│       └── package.json
├── services/
│   └── api/
│       ├── src/main/java/com/mytoday/
│       ├── src/main/resources/
│       │   ├── application.yml
│       │   └── db/migration/
│       ├── src/test/java/com/mytoday/
│       └── build.gradle
├── infra/
│   ├── compose.local.yml
│   ├── compose.prod.yml
│   ├── reverse-proxy/
│   └── backup/
├── docs/
│   ├── MASTER_SPEC.md
│   ├── API.md
│   ├── ERD.md
│   ├── UI_SCREENS.md
│   ├── ADR/
│   │   ├── 0001-modular-monolith.md
│   │   ├── 0002-frontend-quasar.md
│   │   ├── 0003-finance-ledger.md
│   │   └── 0004-stock-data-license.md
│   ├── SECURITY.md
│   ├── OPERATIONS.md
│   └── RELEASE_CHECKLIST.md
└── scripts/
    ├── dev-up.sh
    ├── dev-down.sh
    └── restore-test.sh
```

### 23.2 Git 흐름

- `main`: 항상 배포 가능한 브랜치.
- `feat/<scope>-<description>`: 작은 기능 단위 작업.
- `fix/<scope>-<description>`: 버그 수정.
- PR에는 요구사항 ID, API/DB 변경, 테스트 결과, 관련 스크린샷(민감정보 삭제), 롤백 방안 작성.
- 커밋 메시지 예: `feat(finance): add balanced transaction posting`.
- GitHub Projects 칸: `Backlog / Ready / In Progress / Review / Done`.
- 병렬 개발 시 DB migration 파일은 충돌 가능하므로 순서·버전 예약.

### 23.3 코드 표준

| 범주 | 규칙 |
|---|---|
| Java | DTO/도메인 엔티티 분리, Spring `@Transactional` 경계 명확히 |
| DB | Flyway migration만 사용, 운영 `ddl-auto=validate` 권장 |
| API | OpenAPI spec 및 계약 테스트 |
| Money | Java `BigDecimal`, 금액 객체 `Money(amount,currency)` 도입 |
| Dates | `Instant` / `LocalDate` / `ZoneId` 용도 구별 |
| Security | 서비스 메서드 owner 검증과 통합 테스트 필수 |
| UI | TypeScript strict, 컴포넌트 공통화, 서버 상태와 입력 폼 상태 분리 |
| Logs | 요청 상관 ID, PII/토큰/금융정보 마스킹 |
| Tests | 신규 도메인 정책마다 단위 테스트 먼저 작성 |
| Docs | `MASTER_SPEC`, ADR, API 변경을 PR과 함께 갱신 |

### 23.4 AI 코딩 에이전트 작업 원칙 (Codex/Claude 등 사용 시)

```text
1. docs/MASTER_SPEC.md를 구현 계약의 최상위 요구사항으로 읽는다.
2. 하나의 PR에서 하나의 도메인 유스케이스를 완결한다.
3. DB 테이블/스키마 변경 전 Flyway migration과 rollback 계획을 정리한다.
4. 사용자별 IDOR/원장 정합성/멱등성 테스트를 반드시 추가한다.
5. 시세 API/외부 서비스 문서에 없는 필드나 사용권을 상상해서 구현하지 않는다.
6. 외부 API 키를 클라이언트 코드, 테스트 스냅샷, 로그에 남기지 않는다.
7. 기존 README에 없는 기능을 이미 구현됐다고 표시하지 않는다.
8. 완료 후 구현 파일, 테스트 결과, 미구현 사항, 남은 리스크를 Markdown으로 보고한다.
```

## 24. 우선순위 백로그

기능 단위로 즉시 GitHub Issue로 전환할 수 있도록 ID·우선순위·의존성을 부여한다. `P0`는 최초 사용 제품 필수, `P1`은 핵심 확장, `P2`는 후속.

| ID | 우선순위 | 개발 항목 | 선행 | 수용 기준(요약) |
|---|---|---|---|---|
| CORE-001 | P0 | 모노레포/CI/로컬 개발환경 | 없음 | 웹·API·DB 실행, CI 성공 |
| CORE-002 | P0 | 인증·세션·사용자 설정 | CORE-001 | 사용자별 데이터 격리 |
| AUTH-001 | P0 | 첫 화면·로그인·회원가입·입력 검증·오류 UX | CORE-001 | 미로그인 `/today` 진입 시 로그인 이동, 성공 시 홈 |
| AUTH-002 | P0 | Spring Security 인증·CSRF·세션 회전·로그아웃 | CORE-001 | 쿠키 보안·로그아웃 후 접근 차단 |
| AUTH-003 | P0 | 초기 설정/시간대·통화·온보딩 건너뛰기 | AUTH-001, AUTH-002 | 최초 1회 설정 후 동일 계정 데이터 유지 |
| AUTH-004 | P0 | 계정 간 권한 격리·로그인 속도 제한·보안 이벤트 | AUTH-002 | IDOR·무차별 대입 방어 테스트 통과 |
| AUTH-005 | P1 | 이메일 검증·비밀번호 분실/재설정·메일 전송 | AUTH-002 | 만료/1회성 토큰·계정 존재 유출 방지; 공개 가입 전 필수 |
| AUTH-006 | P1 | 모든 기기 세션 조회·철회 / 최근 로그인 재확인 | AUTH-002 | 다른 기기 세션 만료 확인 |
| AUTH-007 | P2 | Google 로그인 / WebAuthn 패스키 | AUTH-002 | 계정 연결·중복 식별·실기기 보안 검증 |
| CORE-003 | P0 | 공통 오류/페이징/시계/멱등 기본 | CORE-001 | 계약 테스트 |
| UI-001 | P0 | 반응형 레이아웃/내비게이션 | CORE-001 | 모바일/PC 대응 |
| UI-002 | P0 | 통합 홈 카드/빠른 입력 | CORE-002 | 각 화면 이동/입력 |
| TASK-001 | P0 | 할 일 CRUD/완료·연기 | CORE-002 | 동시 수정 충돌 처리 |
| TASK-002 | P1 | 프로젝트/태그/반복 작업 | TASK-001 | 날짜별 중복 생성 방지 |
| CAL-001 | P0 | 내부 일정 CRUD/달력 뷰 | CORE-002 | 시간대/종일 처리 |
| CAL-002 | P1 | 반복 일정·예외일 | CAL-001 | 단일 발생 수정/취소 |
| CAL-003 | P1 | Google 읽기 동기화 | CAL-001, CORE-002 | syncToken/410/중복 복구 |
| RUT-001 | P0 | 루틴 정의·요일별 인스턴스 | CORE-002 | 과거 목표 snapshot |
| RUT-002 | P0 | 날짜별 실적 체크·집계 | RUT-001 | 기간 달성률 정확 |
| ACT-001 | P0 | 실제 활동 CRUD·분야·단위 | CORE-002 | 서로 다른 단위 미합산 |
| ACT-002 | P0 | 할 일/루틴과 활동 연결 | ACT-001, TASK-001 | 이중 집계 없음 |
| GOAL-001 | P1 | 목표 정의·추적·회고 | ACT-001 | 집계 원천 조회 가능 |
| MNY-001 | P0 | 계좌/분류·가상계정 | CORE-002 | 사용자 격리 |
| MNY-002 | P0 | 이중 원장/수입/지출 | MNY-001, CORE-003 | 합계=0·원자적 저장 |
| MNY-003 | P0 | 이체·카드대금·정정 | MNY-002 | 중복 소비 없음 |
| MNY-004 | P0 | 예산·월간 소비 리포트 | MNY-002 | 확정 거래 기준 |
| MNY-005 | P1 | 고정비·예정 현금흐름 | MNY-004 | 실제 잔액과 분리 |
| MNY-006 | P2 | CSV import/dedupe | MNY-002 | 미리보기/중복검증 |
| INV-001 | P0 | 종목 마스터/관심목록 | CORE-002 | 시장별 종목 구별 |
| INV-002 | P0 | 시세 공급자·캐시·신선도 | INV-001 | 타입/시각/오류 구분 |
| INV-003 | P1 | 투자계좌·매매·원가 | INV-001, MNY-001 | 이중 자산 합산 방지 |
| INV-004 | P1 | 가격 알림·쿨다운 | INV-002 | 중복 알림 방지 |
| INV-005 | P2 | 배당/분할/기업공시 | INV-003 | 평가 원가 조정 기록 |
| NOTI-001 | P0 | 인앱 알림함 | CORE-002 | 읽음/삭제/설정 |
| NOTI-002 | P1 | 예약알림·PWA 푸시 | NOTI-001 | quiet hours 준수 |
| INTEG-001 | P1 | Outbox 워커·재시도 | CORE-003 | 중복 소비 방지 |
| INTEG-002 | P1 | 외부 OAuth 연결 상태/해제 | CORE-002 | 토큰 암호화·철회 |
| DASH-001 | P0 | 오늘 홈 집계 API | TASK-001, CAL-001, MNY-004, INV-002 | 일부 공급자 실패 격리 |
| DASH-002 | P1 | 주간 요약/위젯 편집 | DASH-001, GOAL-001 | 사용자별 설정 유지 |
| SEARCH-001 | P1 | 통합 검색 | 주요 도메인 CRUD | 다른 사용자 결과 차단 |
| SEC-001 | P0 | IDOR/CSRF/비밀정보/레이트리밋 | CORE-002 | 보안 테스트 통과 |
| OPS-001 | P0 | 백업·복구·모니터링 | CORE-001 | 격리 환경 복원 성공 |
| OPS-002 | P1 | PWA 설치/오프라인 읽기 | UI-001 | 미연결 시 상태 설명 |
| OPS-003 | P1 | 데이터 내보내기/삭제 | CORE-002 | 사용자 데이터 완전성 |
| MOBILE-001 | P2 | 후속 Capacitor iOS/Android 패키징·OS 카메라·보안 저장소 | UI-001, CORE-002 | 진행 결정 시 양 OS 빌드 검증 |
| MOBILE-002 | P2 | 후속 앱스토어 배포 설정·푸시·심사 준비 | MOBILE-001, NOTI-001 | 진행 결정 시 스토어 테스트/권한 고지 검증 |
| OCR-001 | P1 | PWA 영수증 촬영·이미지 선택 UI | UI-001 | iPhone Safari/Android Chrome/PC에서 촬영 또는 파일 선택 성공 |
| OCR-002 | P1 | 한국어 OCR·텍스트 구조화·검토 초안 | OCR-001, MNY-002 | 금액·일시·가맹점 후보 제시, 불확실성 표시 |
| OCR-003 | P1 | 중복 검사·원장 확정·연결 | OCR-002, MNY-003 | 중복으로 두 건 등록되지 않음 |
| OCR-004 | P2 | 품목별 지출 분할·고도화 파서 | OCR-003 | 할인·취소·품목 합계 처리 |
| AI-001 | P2 | 설명 가능한 규칙 기반 인사이트 | DASH-002 | 근거 지표 제공 |
| AI-002 | P2 | 생성형 AI 보조 기능(선택) | AI-001 | 동의·비용·실행 안전장치 |

### 24.1 개발을 시작할 첫 10개 이슈 순서

1. `CORE-001` — Gradle/Spring Boot/Quasar/PostgreSQL 실행 및 CI.
2. `CORE-002` + `AUTH-001~004` — 로그인·회원가입·첫 화면·세션/CSRF·온보딩·보안 기본. (`AUTH-005`는 공개 가입 전 선행)
3. `CORE-003` — 표준 API 오류, Clock, ID 생성 및 멱등 도구.
4. `UI-001` — 모바일/PC 공통 기본 레이아웃.
5. `TASK-001` — 할 일 CRUD 및 완료 처리.
6. `CAL-001` — 내부 일정 CRUD 및 달력 뷰.
7. `RUT-001` — 루틴 정의/발생 생성.
8. `ACT-001` — 실제 활동 기록 및 단위별 누적.
9. `RUT-002` + `ACT-002` — 실행 기록 연동/중복 방지.
10. `UI-002` — 오늘 홈의 일정·할 일·루틴·활동 카드.

이후 금융 정합성 테스트를 먼저 설계하고 `MNY-001~004`, 그다음 `INV-001~002` 순으로 확장한다. 투자 시세·외부 OAuth보다 **내가 매일 쓰는 루틴/할 일 경험**을 먼저 안정화한다.

## 25. 출시 판단 기준과 미결 사항

### 25.1 개인용 첫 출시(M0) 게이트

- [ ] 첫 실행 시 로그인/회원가입 화면이 표시되고, 인증 후에만 대시보드 진입 가능.
- [ ] 회원가입/로그인·로그아웃·세션 만료·온보딩·다른 사용자 데이터 접근 차단 테스트 완료.
- [ ] 공개 가입을 제공한다면 이메일 검증과 안전한 비밀번호 재설정/메일 발송 구현 완료.
- [ ] iPhone·Android에서 Safari/Chrome PWA 홈 화면 설치·동일 계정 로그인 가능(네이티브 스토어 등록은 범위 밖).
- [ ] 오늘 화면에서 일정·할 일·루틴·지출·관심종목 조회 가능.
- [ ] 할 일 생성/완료가 모바일과 데스크톱에서 정상 동작.
- [ ] 실제 활동을 기록하고 누적량/목표 달성률이 정확히 보임.
- [ ] 가계부 수입/지출/이체/예산이 회계 테스트를 통과.
- [ ] 시세가 없는 경우 `미연결/미제공` 표시, 지연 가격은 기준시각 표시.
- [ ] API 키/토큰/개인 지출 기록이 공개 저장소에 없음.
- [ ] DB 백업 복원 테스트 성공.
- [ ] 앱 실행/배포/장애복구에 관한 README/운영 문서 준비.
- [ ] 운영환경 HTTPS/쿠키/보안 헤더/로그 마스킹 설정.

### 25.2 공개 서비스 전환 게이트

- [ ] 제3자에게 시세 화면 표시 가능한 데이터 제공 계약·라이선스 확보.
- [ ] Google OAuth 앱 검증/공개 서비스 동의화면·도메인 설정 검토.
- [ ] 개인정보처리방침·이용약관·삭제/수출 및 사고 대응 정책 검토.
- [ ] 부하 테스트/모니터링/장애 알림/공급자 rate limit 대응 검증.
- [ ] 외부 시스템 연결 권한/신용카드·증권 기능의 합법적 범위 검토.
- [ ] 백업/재해복구/서버 운영 책임자와 절차 확정.

### 25.3 설계 중 결정해야 할 항목(ADR 작성 필요)

| 결정 주제 | 권장 초안 | 변경 가능 조건 |
|---|---|---|
| 인증 | 개인용 Google OIDC + 서버 세션 | 이메일 독립 계정이 필요할 때 |
| 프런트 | Vue 3 + Quasar | 모바일 네이티브 요구가 커질 때 |
| 백엔드 | Java 21 + Spring Boot | 유지보수 LTS 정책 변경 시 |
| 원장 | 사용자가 인식하는 간단 입력 + 내부 이중 posting | 세무/복식 회계 고도화 필요 시 |
| 투자 원가 | 이동평균법 등 1개 방식 확정 후 도입 | 국가별 세법/브로커 데이터 차이 발생 시 |
| 시세 | 개인용 사용권 충족 공급자, UI는 신선도 표시 | 공개 서비스 라이선스 확보 시 |
| Google 일정 | M1 읽기 전용 | 양방향 충돌 정책 완성 시 |
| 오프라인 | 캐시 읽기 중심 | 동기화 엔진 도입 후 편집 확대 |
| AI | 기본 OFF, 규칙 기반 우선 | 사용자가 명확히 원하고 비용/보안 검토 완료 시 |
| 통화 | KRW 기본, 원천 금액 통화 보존 | 복잡한 해외 금융상품 확대 시 |
| 운영 | 단일 서버 Docker Compose | 사용자/작업량/보안 요구 증가 시 |

### 25.4 가장 중요한 구현 리스크 10개

1. **금융 원장 정합성:** 이체·카드 결제·환불·외환·투자 원금 이동의 이중 집계.
2. **시세 재배포 권한:** 개인용 API를 공개 사용자에게 그대로 제공하는 문제.
3. **캘린더 반복 일정:** 타임존·서머타임·단일 회차 수정/삭제.
4. **동시 수정:** 여러 기기에서 할 일·가계부를 중복 등록하거나 덮어쓰는 문제.
5. **외부 동기화:** 토큰 만료, 중복 데이터, 누락/삭제 이벤트.
6. **투자 수익률:** 현지 통화 손익과 원화 환산 손익의 혼동.
7. **UI 복잡성:** 기능을 많이 넣어 첫 화면이 읽기 어려워지는 문제.
8. **개인정보 보호:** 민감한 소비 내역과 투자 보유정보 노출.
9. **알림 피로:** 작은 주가 변동/반복 루틴의 잦은 알림.
10. **유지비/장애:** 무료 티어 제한, 주가 API 할당량, 백업 실패, 푸시 정책 변화.

### 25.5 이 앱이 차별화되어야 하는 최종 기준

MY TODAY의 경쟁력은 단순히 **할 일+가계부+주식 앱을 합쳤다**는 데 있지 않다.

- **행동 연결:** 오늘 일정→실행→실제 활동→목표 달성으로 이어지는 흐름.
- **돈과 시간의 연결:** 수입/지출/예산/투자 평가액을 분리하되 같은 하루 맥락에서 이해하도록 함.
- **확실한 수치:** 모든 금액·성장률·주가가 원천, 시점, 계산 근거를 갖는다.
- **사용자 제어:** 자동화는 편의를 위한 도구이고, 자기 기록을 사용자가 소유·수정·내보낼 수 있다.
- **오늘 중심 경험:** 각 기능으로 이동하지 않아도 지금 해야 할 행동과 주의할 정보를 알 수 있다.

## 26. 공식 참고 링크

> 아래는 계획 수립 시 참고한 **공식 문서 링크**다. API 권한·가격·버전·정책은 변경될 수 있으므로 실제 개발/공개 배포 전 재검증해야 한다. 2026-11-01 확인 기준으로 작성했으며, 유료 계약 조건을 대체하지 않는다.

### 외부 연동·금융 데이터

1. [Google Calendar — Incremental synchronization](https://developers.google.com/workspace/calendar/api/guides/sync) — `nextSyncToken`, 초기/증분 동기화, 페이지 처리.
2. [Google Calendar — Push notifications](https://developers.google.com/workspace/calendar/api/guides/push) — HTTPS watch, 채널, 변경 알림, 갱신.
3. [Google Calendar — OAuth scopes](https://developers.google.com/workspace/calendar/api/auth) — 최소 권한 스코프.
4. [Google — Sensitive scope verification](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification) — 공개 앱 검증/예외 조건.
5. [KIS 한국투자증권 Open API 안내](https://apiportal.koreainvestment.com/about-open-api) — 개인용 시세 사용 범위·제3자 제공 제한.
6. [KIS API 서비스 요약](https://apiportal.koreainvestment.com/apiservice-summary) — REST/WebSocket 및 인증 방식.
7. [KIS Open API 개발자센터](https://apiportal.koreainvestment.com/) — 호출량·API 변경·폐지 공지 확인.
8. [Twelve Data 공식 요금/라이선스 분류](https://twelvedata.com/pricing) — 개인/상업, internal display 등 상품 차이.
9. [OpenDART 금융감독원](https://opendart.fss.or.kr/) — 기업 공시 오픈API, 시세가 아닌 공시/재무 데이터.

### 개발·운영 문서(구현 전 버전 확인)

10. [Spring Boot 공식 문서](https://docs.spring.io/spring-boot/index.html)
11. [Spring Security 문서](https://docs.spring.io/spring-security/reference/)
12. [Quasar 공식 가이드](https://quasar.dev/)
13. [Capacitor 공식 문서](https://capacitorjs.com/docs)
14. [PostgreSQL 공식 문서](https://www.postgresql.org/docs/)
15. [Flyway 문서](https://documentation.red-gate.com/fd)
16. [OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/)
17. [Web Content Accessibility Guidelines(WCAG)](https://www.w3.org/WAI/standards-guidelines/wcag/)

---

## 27. 웹·iOS·Android 멀티플랫폼 구현 명세

> **현재 결정 (ADR-0005 개정 제안):** 1차는 **무료 PWA**를 PC/아이폰/안드로이드에서 사용한다. 최초 실행부터 로그인·회원가입을 필수로 하고 서버에서 사용자 데이터를 분리한다. Capacitor 기반 네이티브 앱과 App Store/Google Play 정식 배포는 사용성 검증 후로 미룬다. **PWA와 네이티브의 모든 기능이 같다고 가정하지 않는다.**

### 27.1 제품 지원 목표 및 범위

| 기능/플랫폼 | Web(PC/모바일 브라우저) | iOS(아이폰) | Android(갤럭시 등) | 출시 기준 |
|---|---|---|---|---|
| 오늘 홈·일정·할 일·루틴·성장 | O | O | O | 동일 계정·동일 데이터 |
| 가계부 수동 입력·예산·자산 | O | O | O | 원장 정합성 동일 |
| 관심종목·시세·알림 조건 | O | O | O | 제공 권한/기준시각 동일 |
| 영수증 이미지 파일/앨범 선택 | O | O | O | 권한 거부 시 대안 제공 |
| 영수증 즉시 촬영 | 웹캠/파일 capture 지원 범위 | Safari 카메라 촬영 요청·파일 선택 | Chrome 카메라 촬영 요청·파일 선택 | 허용 실패/미지원 시 이미지 선택 대안 |
| 영수증 OCR | 무료 브라우저 OCR 또는 허용 시 서버 OCR | 동일 웹 OCR/서버 처리 | 동일 웹 OCR/서버 처리 | PWA에서 네이티브 ML Kit 필수 아님 |
| 푸시·로컬 알림 | 브라우저 제약/권한 | iOS 홈 화면 PWA·지원 OS/권한 범위 | Chrome 웹 푸시 지원 범위 | 초기 인앱 알림 우선, 푸시 도착 보장 불가 |
| 생체인증·안전한 자격증명 저장 | 서버 세션 쿠키 우선, WebAuthn 후속 | 동일 세션 쿠키 우선, Passkey 후속 | 동일 세션 쿠키 우선, Passkey 후속 | Face ID/지문은 기본 로그인 필수 아님 |
| 오프라인 열람 | 선택적 캐시 | 선택적 캐시 | 선택적 캐시 | 민감 정보 노출 억제 |
| 앱 설치·배포 | HTTPS URL/PWA | Safari 홈 화면에 추가(PWA) | Chrome 앱 설치/홈 화면에 추가(PWA) | 1차 스토어 비용 0원, 후속 앱스토어 별도 |

**포함하지 않는 가정:** Android 전용 SMS 자동 수집, iPhone의 임의 은행 앱 거래내역 읽기, iOS·Android 전체 앱 화면 자동 캡처는 공통 기능이 아니다. 타 앱 데이터 자동 수집·OS 우회 접근을 기본 요구사항으로 두지 않는다.

### 27.2 기술 선택 — 왜 Quasar PWA 우선 + Capacitor 후속인가

- `apps/web`: Vue 3 + TypeScript + Quasar. 동일 화면·상태관리·도메인 컴포저블을 브라우저, PWA, 후속 Android/iOS 앱에서 최대한 공유한다. 최초 배포는 설치형 PWA로 한다.
- `Capacitor`: **현재는 빌드 필수 조건이 아니다.** 후속 시 Quasar 웹 빌드를 iOS/Android 컨테이너로 패키징하고 Java/Kotlin·Swift 플러그인에 접근한다.
- `services/api`: Java 21 + Spring Boot + PostgreSQL. 일정·원장·지출·시세·계정은 기기별이 아닌 서버에서 한 번만 구현한다.
- `apps/web/src/platform`: 카메라·OCR·알림·안전 저장소를 기능별 추상화한 플랫폼 어댑터. 단순 화면 코드는 `Capacitor.isNativePlatform()` 등에 직접 의존하지 않는다.
- `apps/web/src/features/receipt`: 플랫폼과 독립적인 영수증 검토 폼·추출 결과 표시·중복 확인·원장 확정 흐름.

```mermaid
flowchart TB
  W[PC 웹 / 모바일 웹] --> U[Vue 3 + Quasar 공통 UI]
  I[iOS 앱] --> C[Capacitor 네이티브 브리지]
  A[Android 앱] --> C
  C --> U
  U --> P[ReceiptCapturePort / OCRPort / NotificationPort]
  P --> IP[iOS Swift: 카메라/ML Kit/보안 저장소]
  P --> AP[Android Kotlin: 카메라/ML Kit/보안 저장소]
  P --> WP[Web: 파일 업로드 / 서버 OCR 선택]
  U --> S[Spring Boot REST API]
  S --> DB[(PostgreSQL)]
  S --> MD[시세/일정 공급자 - 별도 연동]
```

**대안:** 앱 전용 복잡한 애니메이션, 대용량 네이티브 백그라운드 처리, 플랫폼 일체감이 주요 경쟁력이 되면 React Native/Expo 또는 Flutter를 별도 ADR로 비교한다. 현재는 단일 개발자 생산성과 Vue 기반 웹 재사용에 우선순위를 둔다.

### 27.3 권장 폴더 구조

```text
apps/web/
  quasar.config.ts
  capacitor.config.ts                # Capacitor 버전과 빌드 모드에 따라 위치 확인
  src/
    features/receipt/
      components/ReceiptCaptureButton.vue
      components/ReceiptReviewForm.vue
      composables/useReceiptImport.ts
      models/receipt.ts
    platform/
      capture/ReceiptCapturePort.ts
      capture/webCapture.ts
      capture/nativeCapture.ts
      ocr/ReceiptOcrPort.ts
      ocr/nativeMlKitOcr.ts
      ocr/webServerFallback.ts
      auth/SecureCredentialStore.ts
      notifications/NotificationPort.ts
    api/receiptApi.ts
  src-capacitor/                    # Quasar Capacitor 모드 기준 예시
    ios/                            # Xcode 프로젝트/네이티브 확장
    android/                        # Android Studio 프로젝트/네이티브 확장
services/api/src/main/java/com/mytoday/
  receipt/
    api/
    application/
    domain/
    infrastructure/
```

> Capacitor 설정 파일 위치/플랫폼 프로젝트 트리는 Quasar CLI·Capacitor 메이저 버전과 프로젝트 초기화 모드에 따라 달라질 수 있다. 실제 설치한 조합에 맞춰 `quasar mode add capacitor` 등 공식 가이드로 초기화한 결과가 우선이다.

### 27.4 플랫폼 어댑터 계약 (프런트)

```ts
export type CaptureOrigin = 'CAMERA' | 'PHOTO_LIBRARY' | 'FILE';
export interface CapturedReceipt {
  mimeType: 'image/jpeg' | 'image/png' | 'image/webp' | 'application/pdf';
  bytes: Blob;
  originalFileName?: string;
  source: CaptureOrigin;
}
export interface OcrLine {
  text: string;
  box?: { x: number; y: number; width: number; height: number };
  confidence?: number | null; // SDK별 미제공이면 null
}
export interface ReceiptOcrPort {
  recognize(input: CapturedReceipt, languages: string[]): Promise<OcrLine[]>;
}
export interface ReceiptCapturePort {
  capture(origin: CaptureOrigin): Promise<CapturedReceipt | null>;
}
```

- SDK의 confidence 값은 플랫폼과 모델에 따라 부재할 수 있으므로 임의 생성하지 않는다.
- UI가 참조하는 타입은 `ReceiptCapturePort`에 고정한다. 기기별 구현 차이는 Adapter 안에 숨긴다.
- PDF는 기본 카메라 촬영 이미지와 처리 경로가 다르므로 후속 범위로 분리 가능.
- Web의 서버 OCR은 업로드 전에 사용자 동의와 보존 정책을 고지한다.

### 27.5 카메라 및 미디어 권한

**iOS**

- `@capacitor/camera`를 사용하여 촬영·앨범 선택을 호출한다.
- `Info.plist`에 실제 이용 이유를 설명한 `NSCameraUsageDescription`, 필요 시 사진 라이브러리 사용 목적 문구를 제공한다.
- 권한 거부/제한된 사진 접근/설정에서 철회된 경우에도 파일 선택 경로를 제공한다.
- 원본 사진이 위치 메타데이터(EXIF GPS)를 포함하면 원칙적으로 제거하고, 원본 보존은 선택적.

**Android**

- 카메라·Android Photo Picker를 우선 활용하고 OS 버전별 파일 접근 정책에 맞춘다.
- 전체 갤러리 접근 권한을 불필요하게 요구하지 않는다.
- Activity 종료/메모리 부족 시 촬영 결과 복원, 앱 백그라운드 복귀 시 중복 등록을 테스트한다.

**Web**

- `<input type="file" accept="image/*" capture="environment">`는 촬영을 요청할 수 있지만 실제 동작은 브라우저/기기에 좌우된다.
- 사용자는 드래그 앤 드롭·파일 선택으로도 업로드할 수 있어야 한다.

### 27.6 인증 및 기기 간 동기화

- 공통 사용자 계정 1개가 웹·아이폰·안드로이드의 데이터 원본을 공유하며, 첫 진입은 `/welcome` 또는 `/login`이다. 최초 로그인 완료 시 `/onboarding` 이후 `/today`로 이동한다.
- 1차 **PWA 웹은 `HttpOnly / Secure / SameSite` 속성의 서버 세션 쿠키**를 사용한다. `/api/v1`을 동일 출처로 리버스 프록시하여 쿠키·CSRF·CORS 운영을 단순화한다.
- 네이티브 앱의 OAuth2/OIDC는 시스템 브라우저와 Authorization Code + PKCE 기반으로 설계하고, 플랫폼별 콜백/딥링크/토큰 재발급 정책을 ADR로 확정한다.
- 네이티브 자격증명은 iOS Keychain/Android Keystore 지원 보안 저장소를 사용한다. 일반 `localStorage`에 refresh token 장기 보관 금지.
- 서버는 매 API 요청마다 사용자를 식별하고 `user_id` 소유권을 검증한다.
- 양방향 동기화의 초기 원칙은 **서버가 최종 권위 원본(authoritative source)**. 목록은 페이지/수정시각으로 조회, 수정은 `version`과 충돌(409)을 처리한다.
- 민감한 가계부·투자 데이터는 알림 내용에 상세 금액 노출 여부를 사용자 설정으로 제어한다.

### 27.7 모바일 오프라인 정책

| 동작 | 온라인 | 오프라인 V1 | 오프라인 V2(후속) |
|---|---|---|---|
| 오늘 목록 보기 | 최신 | 마지막 동기화 내용(명확히 표시) | 동일 |
| 할 일 완료 | 즉시 저장 | 로컬 임시 표시 또는 입력 차단 | 명령 큐+충돌 해결 |
| 지출 확정 원장 | 즉시 확정 | 확정 불가(초안만 로컬 저장) | 재접속 시 사용자 확인 후 전송 |
| 영수증 사진 촬영·OCR | 처리 가능 | 온디바이스 OCR 후 초안 보관 가능 | 재접속 후 원장 확정 |
| 주식 현재가 | 최신 공급자 기준 | 캐시+시세 시각/오프라인 배지 | 동일 |

**절대 금지:** 오프라인 임시 지출을 서버 확정 거래로 표시하거나, 네트워크 회복 시 동일 영수증을 무조건 중복 반영하는 동작.

### 27.8 푸시/알림과 백그라운드 작업

- 서버 알림 데이터 모델은 통합. 채널은 `IN_APP`, `WEB_PUSH`, `IOS_PUSH`, `ANDROID_PUSH`, `LOCAL`로 분리.
- iOS 원격 푸시는 APNs 경로/설정, Android는 FCM 경로/설정 검토. 실제 도착 시간은 OS 배터리·권한·집중모드에 좌우되므로 정시 보장 불가.
- 기기별 푸시 토큰은 `device_registration(user_id, platform, push_token_hash, encrypted_token, last_seen_at, revoked_at)` 등에 저장하고 로그에 토큰을 남기지 않는다.
- 일정 알림은 quiet hours, 타임존, 알림 설정과 서버 중복방지 키를 적용한다.
- 영수증 OCR은 앱 화면에서 빠르게 끝나는 처리를 우선; 대량 이미지/서버 OCR은 비동기 작업으로 분리한다.
- iOS/Android 모두 임의 시점 백그라운드 실행을 보장하지 않으므로 서버 배치 및 OS 알림 서비스를 기준으로 설계한다.

### 27.9 빌드·개발 장비·출시

| 항목 | Web | Android | iOS |
|---|---|---|---|
| 개발 IDE | VS Code/IntelliJ | Android Studio | Xcode (macOS 필요) |
| 테스트 장비 | PC/브라우저 | 실제 Android + 에뮬레이터 | 실제 iPhone + 시뮬레이터 |
| 빌드 산출물 | HTML/CSS/JS 정적 자원 | AAB/APK | IPA(배포 파이프라인) |
| 배포 대상 | HTTPS 서버 | Google Play 내부 테스트/정식 | TestFlight/App Store |
| 추가 검토 | 도메인/HTTPS | 서명 키·Play Console 정책 | Apple Developer 계정·서명·심사 |

- Windows에서 웹/백엔드/Android를 주로 개발할 수 있지만 **iOS 정식 빌드·서명·시뮬레이터 개발에는 macOS/Xcode 환경이 필요**하다.
- App Store/Google Play의 등록 비용, 테스트/심사 규정, 지원 최소 OS는 실제 신청 시점 공식 문서를 다시 확인한다.
- 웹 버전만 래핑한 앱은 스토어 심사에서 추가 사용자 가치가 부족하다고 평가될 가능성이 있어 카메라·오프라인 OCR·네이티브 알림 등 실질적 모바일 기능을 제공한다.

### 27.10 멀티플랫폼 수용 테스트

- [ ] 동일 계정이 PC·iPhone·Android 3개 기기에서 로그인 가능.
- [ ] iPhone에서 등록한 할 일이 Android와 웹에 반영되고, 역방향도 정상.
- [ ] 같은 영수증을 Android/아이폰에서 동시에 등록해도 중복 검사 후 확정 거래가 2건 생기지 않음.
- [ ] 카메라 허용/거부·앨범 선택·이미지 재촬영·앱 중단 후 복구 테스트.
- [ ] 한글 영수증 + 영어 가맹점명 + 원화 금액 조합 인식.
- [ ] 시간대·서머타임·기기 로컬 시간 조작 시 서버 원장이 불일치하지 않음.
- [ ] HttpOnly 세션 쿠키/CSRF/로그아웃·다른 사용자 접근 차단을 iPhone·Android·PC에서 확인.
- [ ] 오프라인 상태에서 캐시 시각 표시, 원장 확정이 잘못되지 않음.
- [ ] PWA 아이콘을 홈 화면에 추가한 뒤 로그인·재실행·삭제까지 확인.
- [ ] (후속 네이티브 단계만) Keychain/Keystore, 스토어 내부 테스트·서명·푸시 검증.

### 27.11 개발 순서 재정의

1. Phase 0: 로그인·회원가입·비인증 가드 + Quasar 반응형 PWA 화면/서비스워커 캐시 정책을 구현한다. iPhone Safari·Android Chrome 테스트부터 수행한다.
2. Phase 1~2: 로그인 후 오늘/일정/루틴을 웹에서 완성. iPhone·Android 홈 화면 설치·동일 계정 동기화를 실기기로 검증한다.
3. Phase 3: 가계부 원장 완성 후 **영수증 촬영/파일 선택·무료 OCR·검토 후 저장**을 PWA에 추가한다.
4. Phase 4~6: 시세·성장·알림을 단일 API로 연동. 무료 호스팅/자체 보유 장비의 용량과 비용 제한을 확인한다.
5. Phase 7(후속 결정): 필요해질 때 Capacitor 네이티브 빌드·푸시/보안·App Store/Google Play 심사를 별도 프로젝트로 진행한다. **1차 PWA 출시의 필수 조건이 아니다.**

---

## 28. 영수증 촬영·OCR·가계부 자동입력 명세

> **기능 목표:** 카메라로 영수증 촬영(또는 스크린샷/앨범에서 선택) → 한국어/영어 OCR → 가맹점·일시·총결제금액·품목·세금/할인 후보 추출 → 카테고리 자동 추천 → **사용자 확인 후** 가계부에 거래 1건으로 확정. 원본 이미지 업로드 없이 동작하는 온디바이스 경로를 우선한다.

### 28.1 기능 범위와 출시 우선순위

| 영역 | 첫 버전(P1) | 후속(P2) |
|---|---|---|
| 종이 영수증 | 사진·갤러리 업로드 | 왜곡 보정·다중 페이지 합치기 |
| 전자 영수증 | 캡처 이미지 업로드 | PDF·메일 연동(동의 필요) |
| 텍스트 인식 | 한국어+영문+숫자+기호 | 일본어 등 다국어 확대 |
| 추출 필드 | 가맹점/일시/총액/통화/결제수단 후보 | 공급가액/부가세/할인/포인트 정밀화 |
| 카테고리 | 가맹점 룰/키워드 기반 추천 | 사용자 수정 이력 기반 개인화 |
| 품목 | 인식되면 목록 표시 | 품목별 예산 분할/수량·단가 정합성 |
| 원장 반영 | **검토 후 1건 확정** | 신뢰도+사용자 승인 정책 내 원클릭 확정 |
| 중복 방지 | 이미지/텍스트 지문 + 거래 후보 | 카드 사용 내역 CSV/외부 계좌 매칭 |
| AI | 기본 OFF, 규칙 기반 | 사용자가 동의할 때만 LLM 후보 추론 |

### 28.2 핵심 UX 시나리오

```text
MY TODAY 홈
  [+] 누르기
    ├─ 할 일
    ├─ 일정
    ├─ 지출 직접 입력
    └─ [영수증으로 지출 입력]
          ↓
      [사진 촬영] [사진/스크린샷 선택] [파일 업로드]
          ↓
      촬영 가이드 표시: 빛 반사/흔들림/영수증 끝부분/최종결제금액
          ↓
      이미지 미리보기 → 자르기/회전/다시 찍기
          ↓
      OCR 수행 (모바일: 기기 내부 우선, 웹: 설정한 서버 처리)
          ↓
      필드 추출/검토 초안 생성 (확정 원장과 분리)
          ↓
      가맹점: 스타벅스                   [수정]
      결제일시: 2026-11-02 12:30       [수정]
      총결제액: 9,500 KRW             [수정] ★ 필수
      카테고리: 카페                    [변경]
      결제 계좌/카드: 미지정            [선택] ★ 원장 확정용
      품목: 아메리카노 2잔 등            [보기]
      OCR 확인 필요: 세금/할인 충돌      [원본 보기]
          ↓
      기존 거래와 일치 후보? -- YES --> [기존 거래에 영수증 첨부] [새 거래] [취소]
          |
          NO
          ↓
      [가계부에 저장] (명시적 사용자 확인)
          ↓
      POSTED 거래 및 posting 생성 → 이번 달 예산/오늘 지출 갱신
```

**UX 원칙:** 자동 인식은 자동 **입력 초안**을 뜻한다. 영수증만 봐서는 '내 지출'인지, 어떤 계좌로 결제했는지, 환불/법인 지출인지 확실하지 않을 수 있어 **무조건 자동 확정 금지**. 초기에는 미지정 결제수단인 경우 원장 확정 전에 선택을 요구한다.

### 28.3 OCR 전략: 로컬 우선·서버 선택적 폴백

1. **iOS/Android:** Google ML Kit Text Recognition v2 (한국어 Korean 모델 + 필요 시 Latin 모델)로 온디바이스 텍스트 블록/줄 인식. iOS는 Apple Vision을 대안으로 기술 검증할 수 있으나 한국어 정확도/SDK 지원 정책을 직접 비교한다.
2. **Web/PWA:** 이미지 파일 전송을 사용자가 동의하면 서버 OCR 공급자로 처리. 웹 자체 OCR은 크기/정확도/속도/초기 모델 다운로드 비용을 검증한 뒤 도입.
3. **백엔드:** OCR 텍스트 + 좌표를 표준 JSON으로 수신하여 가맹점·일시·결제 금액 후보를 추출하는 공통 파서. 클라이언트가 마음대로 전송한 금액을 최종 거래로 믿지 않는다.
4. **분리:** `TextRecognizer`는 글자를 읽는다. 금액·가맹점·결제수단을 **의미적으로 구조화하는 파서**는 별도 기능이다. OCR 성공이 가계부 자동입력 성공을 의미하지 않는다.
5. **언어:** `ko`·`en` 우선, 일본어 영수증 등은 사용자별 언어 설정에 따라 추가 모델 선택. 모델 다운로드 상태/기기 사양에 따라 로컬 OCR 사용 가능 여부가 달라질 수 있다.
6. **비용:** 가능한 한 무료 온디바이스 SDK를 쓰고 사용자 선택으로만 원본 이미지를 서버에 보낸다. 외부 LLM/OCR 공급자는 별도 사용 요금·이용약관 확인.

**참고:** ML Kit Text Recognition v2는 한국어/일본어/라틴 문자 인식을 공식 지원한다. 실제 최소 OS/SDK/모델 의존성 버전은 릴리스 시점 호환성 매트릭스를 다시 확인한다.

### 28.4 이미지 보정·입력 정책

- 허용 형식: `JPEG`, `PNG`, `WebP`를 1차 대상으로 하고, `HEIC/HEIF`는 플랫폼에서 지원되는 경우 디코딩한 뒤 표준 형식 변환. PDF는 후속 처리 경로.
- 파일 크기 제한(예: 최대 15MB)은 **제품 설정값**이며 필요에 따라 조정; 초과 시 압축/재촬영 유도.
- EXIF 방향 보정, 좌우 회전, 가장자리 자르기, 그레이스케일/대비 증가, 원본과 보정본 비교는 품질 테스트로 선택.
- 해상도 축소가 작은 품목 글자를 지우지 않도록 글자 높이·픽셀 조건을 테스트. 금액 일부가 잘릴 때 자동 확정 금지.
- 얼굴, 전화번호, 주소, 카드 일부 번호 등 영수증에 불필요한 개인식별정보가 들어갈 수 있음을 전제로 보호.
- 앱은 JPEG 안의 임의 EXIF 메타데이터를 필요 이상 저장하지 않는다.
- 업로드를 허용한다면 확장자뿐 아니라 파일 시그니처 검사/디코딩/용량·픽셀수 제한, 악성 이미지 방어, 이미지 저장소의 접근 제어가 필요하다.

### 28.5 추출 결과 공통 계약

```ts
export type ReceiptParseStatus =
  | 'CREATED' | 'OCR_PROCESSING' | 'REVIEW_REQUIRED'
  | 'READY_TO_CONFIRM' | 'CONFIRMED' | 'DUPLICATE_LINKED'
  | 'CANCELLED' | 'FAILED';

export interface ReceiptDraft {
  receiptId: string;
  status: ReceiptParseStatus;
  merchantName?: string;
  purchasedAt?: string;        // ISO 8601, 시간대 명시
  currency?: string;           // KRW, JPY, USD 등
  totalAmount?: string;        // BigDecimal 문자열
  taxAmount?: string;
  discountAmount?: string;
  paymentMethodHint?: 'CASH' | 'CREDIT_CARD' | 'DEBIT_CARD' | 'UNKNOWN';
  last4Hint?: string;          // 있으면 마스킹, 전체 PAN 저장 금지
  categoryIdSuggestion?: string;
  items: Array<{
    label: string;
    quantity?: string;
    unitPrice?: string;
    lineTotal?: string;
  }>;
  warnings: string[];
  reviewRequiredFields: string[];
  duplicateCandidates: Array<{
    transactionId: string;
    reasonCodes: string[];
  }>;
  originalImageRetention: 'NONE' | 'TEMPORARY' | 'USER_SAVED';
  version: number;
}
```

- `totalAmount`는 '공급가액', '부가세', '거스름돈', '받은 금액', '승인금액', '할인 후 금액' 등을 구분해 추출한 최종 실제 결제액이다.
- OCR 엔진 자체 신뢰도와 파서 필드 신뢰도는 별개. 불명확하면 `null`과 `warnings`를 반환한다.
- 고객의 결제수단과 은행 계좌는 영수증 인식으로 확정되지 않는다. 과거 선택을 추천할 수 있어도 사용자 확인 필요.
- OCR 결과 오류가 있어도 원본 이미지는 사용자가 보며 필드를 수정할 수 있다.

### 28.6 파서: 금액·날짜·상호 추론 규칙

1. OCR의 읽기 순서/좌표를 복원한다. 숫자 `5,000`, `5000`, `￦5,000`처럼 정규화하되 원문도 보존.
2. 영수증 하단의 `합계`, `결제금액`, `승인금액`, `받을금액`, `TOTAL` 등 **주변 키워드와 위치**를 함께 사용해 금액 후보를 찾는다.
3. `합계금액`과 `결제금액`이 다르면 할인/포인트/봉사료/세금을 분석해 마지막 실제 청구액을 사용하며, 모순되면 검토 필요로 표시한다.
4. 결제 시간은 `YYYY-MM-DD`, `YYYY.MM.DD`, `YY/MM/DD`, `오후 3:10` 등의 형식을 지원하되, 임의의 연도를 보정하지 않는다. 영수증 현지 시간대/국가를 알 수 없으면 시간대 확인을 요구.
5. 상호는 상단의 큰 글씨, `상호`, `가맹점`, 사업자등록번호 주변 키워드 등을 참고한다. 사업자번호는 유효성 검사만 수행하고 마스킹/비보존을 기본으로 한다.
6. 품목명과 합계는 표 형태 좌표로 묶고, 할인/취소/환불 행은 음수인지 별도 식별한다.
7. 품목 합계와 총결제액이 불일치하면 `ITEM_SUM_MISMATCH` 등 경고. 영수증 할인/세금/반올림 때문에 정상 불일치 가능하므로 무조건 오류로 판단하지 않는다.
8. 통화 기호 `¥`는 JPY/CNY 등 다의성이 있으므로 가맹점 국가/문맥을 활용하되 모르면 확인 요구.
9. 결제 수단이 명시돼 있지 않거나 단순 카드 번호 일부만 있는 경우 서버의 실제 계좌 ID를 임의로 선택하지 않는다.
10. 증빙서류가 영수증이 아니라 환불확인서/주문내역/입금확인서/견적서인 경우 **실제 결제 발생 여부를 질문**한다.

### 28.7 가계부 카테고리 자동추천

- 1순위: 사용자 확정 규칙 `(정규화 가맹점 → categoryId)`.
- 2순위: 사용자의 이전 수정 이력(같은 매장/유사 품목).
- 3순위: `merchant_keyword_rule` 등 규칙 기반 기본 추천(카페/식비/교통/쇼핑 등).
- 4순위: 사용자 명시 동의 시 AI 추천(출력은 추천일 뿐 자동 확정 아님).
- 같은 가맹점에서 식품·잡화를 함께 산 경우 M1은 대표 카테고리 1개를 추천하되 M2에서는 **품목별 지출 분할**로 확장한다.
- 사용자가 `스타벅스 → 식비`로 수정하면 본인 계정에서만 선택적으로 학습 규칙을 저장한다.

### 28.8 중복 방지·거래 연결

**중복은 2종류다.** (A) **동일 영수증 파일 재등록**, (B) **이미 수동 입력하거나 카드/계좌 내역에서 들어온 거래와 중복**.

- A: 사용자 범위에서 이미지 정규화 해시(예: SHA-256)와 OCR 정규화 텍스트 지문을 활용. 사진 각도/크롭 차이로 해시가 달라질 수 있으므로 해시 1개로만 판정하지 않는다.
- B: 사용자·통화·확정 금액·허용 시간 오차·가맹점 유사도·결제수단 힌트를 조합해 중복 **후보**만 반환.
- 정확히 일치하는 금액과 시간에도 **같은 매장에서 연속 결제 2건**이 가능하므로 후보를 자동으로 병합하지 않는다.
- 검토 UI 선택: `이미 등록한 거래에 영수증만 연결` / `별개 지출로 등록` / `취소`.
- 서버 확정 명령에는 `Idempotency-Key`, import ID, 버전으로 동시 처리/재요청 방지. 동일 import가 이미 확정된 경우 기존 transaction ID를 반환한다.
- 보유 계좌·카드 내역을 외부에서 가져오는 시점에는 receipt link를 사용해 수입/지출 중복을 조정한다. 원장 posting을 재중복 생성하지 않는다.

### 28.9 데이터베이스 설계

| 테이블 | 주요 컬럼 | 제약/역할 |
|---|---|---|
| `receipt_import` | id, user_id, status, origin, ocr_engine, ocr_lang, content_fingerprint, source_hash, occurred_at_candidate, total_candidate, currency_candidate, merchant_candidate, draft_json, retention_mode, created_at, updated_at, version | 인식 작업 및 미확정 초안. FK로 거래 참조 가능 |
| `receipt_ocr_line` (선택) | id, receipt_id, ordinal, text_redacted, bounding_box_json | 원본 OCR 결과. 개인정보 보존 최소화 |
| `receipt_item` | id, receipt_id, line_index, name, quantity, unit_price, line_total, corrected | 품목 인식/수정 결과. 합계 참고용 |
| `receipt_transaction_link` | id, receipt_id, money_transaction_id, link_type, created_at | 영수증 ↔ 확정 거래, 중복 연결 포함 |
| `receipt_processing_job` (서버 OCR 시) | id, receipt_id, status, attempt, last_error_code, next_retry_at | 서버 OCR 재시도/실패 격리 |
| `receipt_image_object` (선택) | id, receipt_id, object_key, encrypted, expires_at, consent_at | 이미지 보관 동의가 있을 때만 |
| `merchant_category_preference` | user_id, normalized_merchant, category_id, priority, updated_at | 사용자별 카테고리 추천 규칙 |

**핵심 불변식**

- 검토 중 `receipt_import.status=REVIEW_REQUIRED`인 데이터는 금융 원장 잔액에 영향을 주지 않는다.
- `receipt_transaction_link`는 `receipt_import`와 `money_transaction` **두 쪽 모두 같은 user_id**인지 서비스/DB 설계에서 보증한다.
- `status=CONFIRMED` 완료와 금융 거래·posting 생성은 하나의 DB 트랜잭션으로 처리(이미지 작업 자체는 트랜잭션 밖).
- 동일 receipt에서 반복 confirm으로 거래가 중복 생성되지 않도록 사용자 범위에서 확정 상태 전이 + 멱등 키 보장.
- 저장형 이미지 객체키는 권한 검사를 거치는 URL로만 접근. 공개 URL/퍼블릭 버킷 금지.
- 스키마에서는 같은 영수증에 여러 거래를 연결할 가능성을 고려하되 첫 버전 **정상 지출 1건 연결**로 제한.

### 28.10 PostgreSQL 초기 마이그레이션 예시

> 이 DDL은 **기존 16장 스키마와 합치기 위한 확장 초안**이다. 사용자 PK·거래 PK 타입이 UUID임을 확인하고 Flyway 마이그레이션 번호를 충돌 없이 배정한다.

```sql
CREATE TABLE receipt_import (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  status VARCHAR(32) NOT NULL,
  origin VARCHAR(24) NOT NULL,
  ocr_engine VARCHAR(40),
  source_hash CHAR(64),
  content_fingerprint CHAR(64),
  merchant_candidate VARCHAR(255),
  occurred_at_candidate TIMESTAMPTZ,
  total_candidate NUMERIC(20, 6),
  currency_candidate CHAR(3),
  draft_json JSONB NOT NULL DEFAULT '{}'::jsonb,
  retention_mode VARCHAR(20) NOT NULL DEFAULT 'NONE',
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT receipt_import_status_ck CHECK (
    status IN ('CREATED','OCR_PROCESSING','REVIEW_REQUIRED',
               'READY_TO_CONFIRM','CONFIRMED','DUPLICATE_LINKED',
               'CANCELLED','FAILED')
  ),
  CONSTRAINT receipt_import_amount_ck CHECK (
    total_candidate IS NULL OR total_candidate >= 0
  )
);

CREATE INDEX idx_receipt_import_user_created
  ON receipt_import(user_id, created_at DESC);
CREATE INDEX idx_receipt_import_user_fingerprint
  ON receipt_import(user_id, content_fingerprint);

CREATE TABLE receipt_transaction_link (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL REFERENCES app_user(id),
  receipt_id UUID NOT NULL REFERENCES receipt_import(id),
  money_transaction_id UUID NOT NULL REFERENCES money_transaction(id),
  link_type VARCHAR(20) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT receipt_link_unique UNIQUE (receipt_id, money_transaction_id),
  CONSTRAINT receipt_link_type_ck CHECK (link_type IN ('CREATED','MATCHED','ATTACHMENT'))
);
CREATE INDEX idx_receipt_link_user_transaction
  ON receipt_transaction_link(user_id, money_transaction_id);
```

**추가 구현 필요:** 위 예시는 FK만으로 다중 테이블의 `user_id` 일치를 강제하지 않는다. 실제 구현에서는 복합 유니크/복합 FK 또는 신뢰할 수 있는 서비스 트랜잭션 검증 및 경합 테스트로 소유권 일치를 보장해야 한다. 상태 전이 및 확정 유일성도 애플리케이션 트랜잭션/제약 조건 설계에 맞춰 보완한다.

### 28.11 REST API 상세

| Method | Endpoint | 목적 | 결과 |
|---|---|---|---|
| `POST` | `/api/v1/receipts/imports` | OCR 줄/이미지 처리 결과로 초안 생성 | `201` + import ID |
| `POST` | `/api/v1/receipts/imports/upload` | 서버 OCR 동의 사용자용 파일 업로드 | `202` + job ID |
| `GET` | `/api/v1/receipts/imports/{id}` | 초안/상태/필드/경고/중복 후보 조회 | `200` |
| `PATCH` | `/api/v1/receipts/imports/{id}` | 가맹점/금액/일시/품목 등 교정 | `200` + 새 version |
| `GET` | `/api/v1/receipts/imports/{id}/duplicates` | 중복 거래 후보 조회 | `200` + 후보 목록 |
| `POST` | `/api/v1/receipts/imports/{id}/confirm` | 검증 후 신규 금융 거래 확정 | `201` 또는 멱등 재요청 시 기존 ID |
| `POST` | `/api/v1/receipts/imports/{id}/link` | 기존 금융 거래에 영수증 연결 | `200` |
| `POST` | `/api/v1/receipts/imports/{id}/cancel` | 초안 폐기/삭제 정책 반영 | `200` |
| `DELETE` | `/api/v1/receipts/imports/{id}/image` | 원본 이미지 보관 해제/삭제 | `204` |

**인증:** 사용자 ID는 body로 받지 않고 서버 인증 주체로 결정. `confirm`은 사용자 소유 계좌/카테고리를 참조하는지 검사. 모든 mutate API는 CSRF/세션 또는 적합한 토큰 정책 적용.

**온디바이스 OCR 결과 전송 예시:**

```http
POST /api/v1/receipts/imports
Content-Type: application/json
Idempotency-Key: 6aac... (UUID 예시)
```

```json
{
  "origin": "CAMERA",
  "ocrEngine": "ML_KIT_V2_ON_DEVICE",
  "recognizedLines": [
    { "text": "스타벅스 OO점" },
    { "text": "2026.11.02 12:30" },
    { "text": "승인금액 9,500" }
  ],
  "sourceFingerprint": "sha256-normalized-content-hash",
  "imageUploadConsent": false
}
```

**검토 후 확정 요청 예시:**

```http
POST /api/v1/receipts/imports/{id}/confirm
Idempotency-Key: <uuid-per-confirm-intent>
If-Match: "7"
Content-Type: application/json
```

```json
{
  "moneyAccountId": "00000000-0000-4000-8000-000000000001",
  "categoryId": "00000000-0000-4000-8000-000000000002",
  "merchantName": "스타벅스 OO점",
  "occurredAt": "2026-11-02T12:30:00+09:00",
  "currency": "KRW",
  "amount": "9500",
  "type": "EXPENSE",
  "duplicateDecision": "CREATE_NEW",
  "memo": "영수증 OCR 등록"
}
```

- 예제 UUID/가맹점/시간은 **가상 테스트 데이터**다.
- 요청의 `duplicateDecision`이 기존 일치 후보를 무시하는 `CREATE_NEW`라면 명시적 최종 확인을 요구한다.
- 같은 import의 중복 confirm 시 **기존 거래를 응답**하고 원장 posting을 새로 추가하지 않는다.
- 409: 초안 버전 충돌/이미 확정된 상태에서 서로 다른 요청. 422: 결제액·통화·계좌·타입 누락. 413: 크기 초과. 415: 미지원 파일. 429: OCR 호출 제한.
- 비동기 OCR이면 `GET` 또는 SSE/폴링으로 상태를 추적하되 202 응답을 최종 확정으로 오인하지 않는다.

### 28.12 백엔드 유스케이스·트랜잭션 의사코드

```java
@Transactional
public ConfirmReceiptResponse confirm(UUID receiptId, ConfirmReceiptCommand command,
                                      AuthenticatedUser currentUser, String idempotencyKey,
                                      long expectedVersion) {
    ReceiptImport receipt = receiptRepository.lockOwnedBy(receiptId, currentUser.id());
    validateVersion(receipt, expectedVersion);

    if (receipt.isConfirmed()) {
        return transactionLinkRepository.getExistingResponse(receiptId);
    }
    validateReviewAndAccountOwnership(receipt, command, currentUser);
    validateAmountCurrencyAndDuplicateDecision(command);

    MoneyTransaction tx = moneyCommandService.postExpense(
        command.toExpenseRequest(), currentUser, idempotencyKey);
    receiptTransactionLinkRepository.link(currentUser.id(), receipt.id(), tx.id(), "CREATED");
    receipt.markConfirmed();
    outbox.append(new ReceiptConfirmedEvent(receipt.id(), tx.id()));
    return new ConfirmReceiptResponse(receipt.id(), tx.id(), "POSTED");
}
```

**구현 주의:** 의사코드로, 실제 `MoneyCommandService`가 동일 DB 트랜잭션에 참여하는지 검증해야 한다. `lockOwnedBy`는 사용자 범위 소유권 + 비관적 잠금 또는 동등한 상태 전이 원자성을 제공해야 한다. 멱등 키 재사용 시 요청 본문 hash가 달라지면 충돌 처리해야 한다.

### 28.13 개인정보·보존 기간

- 기본 정책: 모바일에서는 이미지를 서버에 업로드하지 않고 **OCR 인식 텍스트 중 필요한 필드만 전송**한다.
- 이미지 원본: 기본 `NONE` (서버 미보관). 사용자가 영수증 증빙 저장을 선택하면 암호화된 비공개 저장소에 보관하고 언제든 삭제 가능.
- 서버 OCR을 선택한 경우에만 이미지 송신·일시 저장에 동의. 처리 완료 후 기본 삭제를 목표로 하고 잔존 기간/장애 데이터 삭제 정책을 문서화.
- 일부 카드번호·전화번호·주소·사업자번호·승인번호는 필요성이 없으면 저장하지 않고 마스킹한다.
- 접근 로그/애플리케이션 로그/오류 추적 도구에 OCR 전문·이미지·금융 민감정보를 남기지 않는다.
- 데이터 내보내기/계정 삭제 요청 시 영수증 데이터와 이미지 연결도 포함한다.
- 사용자가 외부 AI 인식/분석을 켜기 전에는 해당 공급자에 영수증 내용을 전송하지 않는다.

### 28.14 비용 및 성능 정책

- M1: 가능하면 온디바이스 OCR 무료 경로. 서버는 파서·검토·원장 확정만 담당해 호출 비용 최소화.
- 로컬 텍스트 모델을 포함하면 앱 번들 크기와 초기 설치 용량이 커질 수 있으므로 실제 앱 크기 측정.
- 이미지 자체를 DB `BYTEA` 컬럼에 대량 저장하지 않는다. 필요 시 별도 오브젝트 스토리지와 짧은 보존기간을 사용.
- OCR 리소스를 부주의하게 반복 실행하지 않도록 동일 파일/작업 ID 캐시. 시스템 권한 거부 시 빠른 실패·재시도 가능 UX.
- 인식 성능 KPI: OCR 처리 시간(기기별 P50/P95), 총액 정확도(확정 값 기준), 사용자의 수정률, 중복 후보 적중률, 잘못 확정된 거래 0건을 중심으로 측정.
- 인식률은 시험 데이터 없이 숫자로 보장하지 않는다. 실제 여러 가맹점 샘플에 대해 측정 후 목표 확정.

### 28.15 테스트 시나리오 최소 22개

| # | 테스트 | 기대 결과 |
|---|---|---|
| 01 | 한글 카페 영수증 | 가맹점·금액 후보 표시 |
| 02 | 편의점 여러 품목 | 총결제액/품목 구분 |
| 03 | 할인/쿠폰 적용 영수증 | 할인 전 합계 대신 실제 최종 결제액 후보 |
| 04 | 부가세·봉사료 별도 | 총액이 세금 포함인지 파서 검토 |
| 05 | 카드 결제·승인번호 포함 | 전체 카드번호 저장 금지 |
| 06 | 현금 영수증 | 현금/현금영수증 구분, 계좌 선택 |
| 07 | 거스름돈 표시 | 건넨 금액을 지출로 저장하지 않음 |
| 08 | 취소/환불 영수증 | EXPENSE 무조건 생성 금지 |
| 09 | 주문서/견적서 | 실제 결제로 단정하지 않음 |
| 10 | 흐림/반사/구겨짐 | 미인식·경고/재촬영 흐름 |
| 11 | 화면 캡처(전자영수증) | 파일 선택·OCR 성공 |
| 12 | 영수증 두 장 동시 업로드 | 각각 별도 초안, 섞이지 않음 |
| 13 | 한국어+영어 혼합 | 언어 혼합 인식 시도 |
| 14 | 해외 통화/외국 날짜 | 통화·시간대 모호성 확인 |
| 15 | 동일 파일 재업로드 | 기존 import 후보 탐지 |
| 16 | 같은 가게 동일 금액 연속 결제 | 일치 후보로만 표시, 자동 병합 X |
| 17 | 다른 기기 동시 confirm | 확정 원장 중복 0건 |
| 18 | 업로드 중 네트워크 종료 | 재시도 가능, 원장 미변경 |
| 19 | OCR 처리 후 사용자가 앱 종료 | 미확정 초안 복구/정리 정책 따름 |
| 20 | 카메라/사진 권한 거부 | 대체 파일 선택/안내 제공 |
| 21 | 다른 계정 receiptId 조회 | 403/404 등 권한 차단 |
| 22 | 영수증 삭제/계정 탈퇴 | 이미지·인식 초안 관련 삭제/보존 정책 준수 |
| 23 | 사용자 총액 수정 | 수정 값만 POSTED 거래로 확정 |
| 24 | 기존 거래 연결 선택 | 새 posting 없이 영수증 링크만 생성 |
| 25 | OCR 전문에 제어문자/스크립트 포함 | 안전한 텍스트 처리 및 XSS 방어 |
| 26 | 같은 멱등키로 다른 거래 요청 | 충돌 반환, 기존 원장 유지 |

### 28.16 작업 단위 및 실행 순서

1. `OCR-001A` — 웹 파일 업로드·이미지 미리보기/회전 UI.
2. `OCR-001B` — Capacitor iOS/Android 카메라/갤러리 및 권한 대응.
3. `OCR-002A` — ML Kit 한국어/라틴어 실제 기기 인식 실험(안드로이드·아이폰 각 10장 이상의 다양한 영수증 샘플로 시작).
4. `OCR-002B` — `ReceiptOcrPort` 표준 스키마·Mock OCR 엔진.
5. `OCR-002C` — 서버 파서: 거래일시·가맹점·총결제액 후보·검토 필요 조건.
6. `OCR-002D` — `receipt_import` Flyway 스키마·초안 CRUD·인식 결과 버전 관리.
7. `OCR-003A` — 중복 후보 탐지 API/검토 화면.
8. `OCR-003B` — 계좌 선택/카테고리 교정/확정 API와 `money_transaction`/`money_posting` 연결.
9. `OCR-003C` — 멱등/동시성/실패/권한/정합성 자동 테스트.
10. `OCR-003D` — iOS/Android/웹 3개 플랫폼 실제 결제 시나리오 리그레션.
11. `OCR-004A` — 품목별 카테고리 분할, 카드/계좌 CSV 연결 및 ML 규칙 고도화(후속).

### 28.17 영수증 기능 수용 기준 (Definition of Done)

- [ ] iOS 앱에서 종이 영수증을 촬영하고 가맹점·일시·최종 결제금액 초안을 검토할 수 있다.
- [ ] Android 앱에서도 동일 시나리오가 동작한다.
- [ ] PC 웹에서 스크린샷/이미지 파일 선택 후 같은 검토 화면을 사용할 수 있다.
- [ ] 한국어 OCR 가능 여부를 실제 기기에서 검증하고 인식 실패 시 수동 입력할 수 있다.
- [ ] 사용자 확인 전에는 금융 원장에 `POSTED` posting이 한 건도 생성되지 않는다.
- [ ] 확정 시 정확히 한 번만 비용/결제계좌 posting을 기록하고 이번 달 예산에 반영한다.
- [ ] 중복 의심 시 기존 거래 연결 또는 신규 거래 생성 중 사용자가 명시적으로 선택한다.
- [ ] 민감한 원본 이미지는 기본적으로 서버에 업로드하지 않는다.
- [ ] OCR/LLM 비용이 발생하는 경로는 동의·쿼터·예상 처리 방식 안내 후 활성화한다.

### 28.18 공식 기술 참조 (구현 전 재확인)

- Capacitor: https://capacitorjs.com/docs
- Capacitor 카메라 플러그인: https://capacitorjs.com/docs/apis/camera
- Capacitor iOS 개발: https://capacitorjs.com/docs/ios
- Capacitor 환경 설정: https://capacitorjs.com/docs/getting-started/environment-setup
- Capacitor 알림: https://capacitorjs.com/docs/apis/push-notifications
- Google ML Kit Text Recognition v2: https://developers.google.com/ml-kit/vision/text-recognition/v2
- Google ML Kit Android: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- Google ML Kit iOS: https://developers.google.com/ml-kit/vision/text-recognition/v2/ios
- ML Kit 언어 지원 목록: https://developers.google.com/ml-kit/vision/text-recognition/v2/languages
- Apple Vision: https://developer.apple.com/documentation/vision/vnrecognizetextrequest

---

## 29. 최초 실행·로그인·회원가입·계정 복구 상세 명세

> **추가 요구사항 AUTH-ADR-001 (2026-11-02):** MY TODAY는 로그인 전에는 사적인 일정·지출·보유주식·성장 데이터를 절대 표시하지 않는다. 첫 방문 시 `환영 → 로그인 또는 회원가입`, 인증 완료 후 `온보딩(최초 1회) → 오늘 홈`으로 이동한다. **1차는 무료 PWA**로 iPhone Safari / Android Chrome / PC 브라우저에서 동일 계정으로 사용한다. 정식 앱스토어 배포는 후순위다.

### 29.1 첫 실행과 재방문 규칙

| 조건 | 열리는 화면 | 후속 처리 |
|---|---|---|
| 첫 방문, 유효 세션 없음 | `/welcome` | `로그인` 또는 `회원가입` 중 선택 |
| `로그인` 선택 | `/login` | 이메일·비밀번호 인증, 성공 시 세션 생성 |
| `회원가입` 선택 | `/signup` | 중복 이메일/유효성 확인, 정책에 따라 이메일 검증 |
| 로그인 성공, 온보딩 미완료 | `/onboarding` | 표시 이름·시간대·통화·목표 선택(건너뛰기 가능) |
| 로그인 성공, 온보딩 완료 | `/today` | 일정·할 일·지출·관심주식 통합 홈 |
| 기존 사용자 재방문, 세션 유효 | `/today` 또는 마지막 허용 화면 | 개인 데이터 재조회 |
| 유효 세션 없이 `/money` 직접 접속 | `/login?next=%2Fmoney` | 로그인 후 안전한 내부 경로로 복귀 |
| 로그아웃 선택 | `/welcome` | 서버 세션 폐기, 개인 화면/캐시 비움 |
| 로그인 세션 만료 | `/login` | 개인 API는 401, 미저장 작업 안내 후 재인증 |
| 네트워크 불가 | 오프라인 안내 | 로그인 성공/실패를 추측하지 않고 금융 원장 입력 차단 |

**원칙:** 단순히 브라우저에 `loggedIn=true`를 저장하지 않는다. **서버의 세션 검증이 로그인 여부의 유일한 기준**이다. 로그인 전 노출 가능한 콘텐츠는 서비스 설명, 개인정보/이용 안내, 인증 화면뿐이다.

```mermaid
flowchart TD
  A[앱 열기 / PWA 실행] --> B{서버 세션 유효?}
  B -->|아니오| C[환영 화면 /welcome]
  C --> D{선택}
  D -->|로그인| E[/login]
  D -->|회원가입| F[/signup]
  F --> G[계정 생성 및 가입 정책 검증]
  G --> E
  E --> H{인증 성공?}
  H -->|아니오| E
  H -->|예| I{최초 설정 완료?}
  B -->|예| I
  I -->|아니오| J[/onboarding]
  J --> K[/today]
  I -->|예| K
  K --> L[일정/할 일/가계부/주식/성장]
  L --> M{로그아웃?}
  M -->|예| N[서버 세션 폐기 + 캐시 정리]
  N --> C
```

### 29.2 UI/UX 요구사항 — PC·아이폰·안드로이드 공통

**① 환영 화면 `/welcome`**

```text
┌───────────────────────────────────────┐
│               MY TODAY                │
│      나의 하루, 돈, 목표를 한곳에       │
│                                       │
│     오늘 일정 / 가계부 / 주식 / 할 일   │
│                                       │
│           [ 로그인 ]                  │
│         [ 회원가입 ]                  │
│                                       │
│   개인정보 안내   ·   이용 안내        │
└───────────────────────────────────────┘
```

- 로그인 전에는 내 돈/일정에 대한 예시 데이터를 실제 내 데이터처럼 보여주지 않는다.
- 모바일(최소 320 CSS px 기준, 기기 확대 접근성 고려)에서 세로 스크롤 없이 주요 버튼 접근을 우선하지만 작은 화면에서 스크롤을 금지하지 않는다.
- 로고/문구는 자산과 무관한 정적 콘텐츠이며 HTTPS로 제공한다.

**② 로그인 `/login`**

```text
┌───────────────────────────────────────┐
│  ← 돌아가기            MY TODAY       │
│                                       │
│             다시 만나서 반가워요      │
│                                       │
│  이메일                               │
│  [ email@example.com              ]   │
│                                       │
│  비밀번호                    [보기]   │
│  [ •••••••••••••••••••••           ]   │
│                                       │
│  [ 로그인                           ] │
│                                       │
│  비밀번호를 잊으셨나요?                │
│  계정이 없나요? 회원가입               │
└───────────────────────────────────────┘
```

- 입력 필드: 이메일(`autocomplete=username`, 모바일 이메일 키보드), 비밀번호(`autocomplete=current-password`).
- 로그인 버튼 중복 제출 방지, 진행 중 상태, 엔터/키보드 제출, 오류 시 포커스 이동, `aria-live` 오류 영역.
- 비밀번호 보기 기능은 화면에서만 토글, 자동 저장 여부는 OS/브라우저 비밀번호 관리자에 위임.
- 실패 시 일반화 문구 `이메일 또는 비밀번호를 확인해 주세요.` 사용(계정 존재 여부 노출 방지).
- 잠금/속도 제한 시 `시도 횟수가 많습니다. 잠시 후 다시 시도해 주세요.` 등 안전한 안내.
- `로그인 유지` 옵션은 후속에서 도입 가능. M0는 브라우저/기기별 세션 만료 정책을 명시하고 자동 로그인 강제 제공 금지.
- 회원가입 버튼은 `/signup`, 비밀번호 분실은 `/forgot-password`로 연결. 단, 메일 재설정 기능 미배포 시 실제 버튼으로 무작동 화면에 보내지 말고 `복구 기능 준비 중`처럼 명시 또는 운영자 지원 방식 안내.

**③ 회원가입 `/signup`**

- 필수: 이메일, 비밀번호, 비밀번호 확인. 선택: 표시 이름.
- 암호 정책: 최소 **12자 권장**, 과도한 특수문자 조합 강제보다 긴 암호/패스프레이즈 허용(허용 최대 길이와 유니코드 처리 명시). 비밀번호 확인은 서버에서 해시 비교 전 일치 검증.
- 이메일 중복, 잘못된 형식, 약한 비밀번호, 429 호출 제한 등을 각 필드에 이해하기 쉬운 오류로 표시한다.
- 기본 설정: 시간대 `Asia/Seoul`, 통화 `KRW`, 언어 `ko-KR`(온보딩 수정 가능).
- 약관·개인정보 안내 링크를 제공하며 동의/고지는 실제 서비스 범위·법적 요건에 따라 분리한다.
- **개인용 개발 베타:** 비용 0원 운영을 위해 초기 사용자 1명은 관리 명령/초대 기반으로 만들 수 있다. 이 경우 공개 회원가입은 비활성화하여 임의 접근을 막는다.
- **공개 회원가입:** 이메일 주소 소유 확인과 메일 발송/계정 복구가 준비된 뒤 오픈한다. `회원가입 화면 존재`와 `누구나 가입 가능`은 별도 설정(`registration.mode=INVITE_ONLY|PUBLIC`)이다.

**④ 최초 설정 `/onboarding`**

- 단계 A: 표시 이름·시간대·통화/국가 기준, 필요한 경우 한 번만.
- 단계 B: 주요 목표(일정/할 일/가계부/주식/성장) 우선 카드 선택. 선택을 강제하지 않는다.
- 단계 C: `시작하기` → `/today`. `나중에 설정` 선택 시 선택항목을 기본값으로 저장한다.
- 튜토리얼 데이터는 실제 가계부에 자동 등록하지 않는다. 데모 카드는 `예시`라는 표시를 한다.
- 온보딩 `completed_at`은 서버에 저장한다. 다른 기기 로그인 시 완료 상태가 공유된다.

**⑤ 설정 / 계정 관리 `/settings/security`**

- 표시 이름 수정, 비밀번호 변경(현재 비밀번호 재확인), 로그아웃.
- M1: 현재 로그인된 기기/최근 로그인 목록, 다른 기기에서 로그아웃, 비밀번호 재설정, Google 계정 연결/해제.
- M2: Passkey(WebAuthn) / 가능한 경우 플랫폼 생체인증 UX. **PWA에서 Face ID를 직접 인증 서버의 비밀번호 대체 수단이라고 단정하지 않는다.**

### 29.3 Router Guard 및 세션 초기화

| 라우트 종류 | 경로 | 정책 |
|---|---|---|
| 공개 | `/welcome`, `/login`, `/signup`, `/forgot-password`, `/reset-password` | 로그인 없이 열림(복구 페이지는 token 검증 필요) |
| 보호 | `/today`, `/calendar`, `/tasks`, `/growth`, `/money`, `/invest`, `/settings` | 서버 세션 유효해야 열림 |
| 온보딩 | `/onboarding` | 로그인은 필요, 이미 완료라면 `/today`로 이동 |
| 인증 API | `/api/v1/auth/**` | 엔드포인트별 공개/인증·CSRF 정책 구별 |
| 사생활 보호 | `/api/v1/money/**`, `/api/v1/invest/**` 등 | API 레벨에서 인증+객체 소유권 검사 필수 |

- 앱 로딩 시 `GET /api/v1/auth/session` 호출. `200`(로그인), `401`(미로그인), `5xx/네트워크 실패`(판정불가)를 구별한다.
- 네트워크 오류를 로그인 실패로 간주하거나 자동 로그아웃 처리하지 않는다. 재시도 UI를 제공하되 민감정보를 화면에 노출하지 않는다.
- `next`/`redirect` 목적지는 **같은 도메인 내 내부 상대 경로만 허용**; 외부 URL 및 `//host` 차단(오픈 리다이렉트 방지).
- 라우터 가드는 UX일 뿐이다. 백엔드의 Spring Security와 사용자별 소유권 검증이 최종 보안 계층이다.
- 로그인 후 이전 사용자의 Vue Query/Pinia 메모리 상태·Service Worker 캐시가 재사용되지 않도록 사용자 전환/로그아웃 시 삭제한다.
- PWA 서비스 워커는 로그인/가계부/투자 등 **개인 API 응답을 캐시하지 않고**, HTML/정적 자산 중심으로 캐시한다. 개인정보 화면은 필요 시 `Cache-Control: no-store`를 적용한다.

### 29.4 인증 방식 및 세션 보안 — Spring Boot

**M0 권장: Spring Security + 서버 세션 쿠키(Spring Session JDBC 선택)**

1. 웹 프런트와 API를 `https://my-today.example` 같은 **동일 출처**로 서비스한다. `/api/v1`은 reverse proxy로 Spring Boot에 전달한다. 도메인 예시는 실제 배포 주소가 아니다.
2. 서버가 서명/인증된 사용자 세션을 관리한다. 인증 성공 시 세션 ID를 회전(session fixation 방어).
3. 브라우저에는 `HttpOnly; Secure; SameSite=Lax` 또는 애플리케이션의 사용 패턴에 적합한 강화 정책의 **세션 쿠키만** 저장한다. 인증 토큰을 `localStorage`에 장기 보관하지 않는다.
4. 모든 POST/PATCH/PUT/DELETE는 로그인 전 인증 엔드포인트를 포함해 CSRF 보호를 적용한다. 초기화 토큰을 `GET /auth/csrf` 등으로 받아 `X-CSRF-TOKEN` 헤더에 포함한다. 구현한 CSRF 저장소/쿠키 방식과 실제 헤더명이 일치해야 한다.
5. 서버는 `request.getSession()`만으로 유저를 신뢰하지 않고 Spring Security `Authentication`에서 검증된 사용자 ID를 사용한다.
6. 로그아웃은 서버 세션 무효화, 쿠키 만료, 현재 브라우저 상태 정리까지 한다. **다른 기기의 세션은 명시적인 '모든 기기 로그아웃' 때만 철회**한다.
7. 데이터 소유권은 `WHERE id = :id AND user_id = :authenticatedUserId`와 서비스 수준 검증을 결합한다.
8. 로그인 시도는 IP + 사용자 식별자 기준으로 속도를 제한하고, 계정 열거를 막는다. 로그인 실패 로그에 비밀번호/전체 이메일을 남기지 않는다.
9. 비밀번호는 **Argon2id 등 적절한 PasswordEncoder 해시**로 저장하고 재설정·변경 시 해시 업그레이드 정책을 적용한다.
10. 초기 세션 권장값(예시): 비활동 만료 7일, 절대 사용 기간 30일. 금융 내보내기·계정 삭제·비밀번호 변경 등 민감 작업은 최근 로그인(예: 10분 이내) 또는 재인증 요구. 실제 세션 설정·사용성 테스트 후 확정.

**주의:** Spring Security의 기본 `formLogin`은 일반적인 form 필드 중심이다. 아래 JSON `/auth/login` 계약을 적용하려면 맞춤 로그인 필터/엔드포인트를 구현하거나, 프런트에서 `application/x-www-form-urlencoded`를 사용하도록 계약을 통일해야 한다. **예시 API가 설정 없이 자동 작동한다고 가정하지 않는다.**

### 29.5 데이터베이스 — 인증 테이블·마이그레이션 예시

현재 16장의 `app_user`(기본 사용자 정보)는 유지하고 인증정보를 별도 관리한다. 다음은 **별도 Flyway 후속 마이그레이션 예시**이며 M0에서 구현할 최소 부분이다.

```sql
-- 예: V2__local_auth_and_onboarding.sql (실제 파일 버전은 기존 Flyway 이력 확인)
CREATE TABLE auth_local_credential (
  user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
  password_hash VARCHAR(255) NOT NULL,
  password_changed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  failed_attempts INTEGER NOT NULL DEFAULT 0,
  locked_until TIMESTAMPTZ,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT ck_auth_failed_attempts CHECK (failed_attempts >= 0)
);

ALTER TABLE app_user
  ADD COLUMN onboarding_completed_at TIMESTAMPTZ;

CREATE TABLE auth_security_event (
  id UUID PRIMARY KEY,
  user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
  event_type VARCHAR(40) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ip_hash VARCHAR(128),
  request_id VARCHAR(100)
);
CREATE INDEX idx_auth_security_event_user_at
  ON auth_security_event(user_id, occurred_at DESC);

-- 선택: Spring Session JDBC를 쓸 때 세션 테이블은
-- Spring Session 공식 스키마를 이관·관리하며 직접 임의 구조를 만들지 않는다.
```

M1에서 `email_verification_token`, `password_reset_token` 테이블을 도입할 때는 다음을 준수한다.

- 메일 링크의 실제 무작위 토큰(최소 128비트 이상의 CSPRNG 엔트로피)은 DB에 **원문이 아닌 해시**로 저장한다.
- `expires_at`, `consumed_at`, 사용자 ID, 발급 시각을 기록해 단회 사용/만료 검증을 한다.
- 비밀번호 재설정 성공 시 기존 세션을 무효화하고 보안 알림을 전송한다(발송 수단이 연결된 경우).
- 계정 상태 `PENDING_EMAIL_VERIFICATION`, `ACTIVE`, `LOCKED`, `DISABLED` 등의 전이를 정의한다. 기존 `app_user.status` 기본값 `ACTIVE`를 **공개 가입에서 그대로 사용하지 않는다**.
- 이메일 표준화 정책은 단일 함수에서 적용하고 정상화 값에 대한 DB 유니크 제약으로 중복 생성을 막는다.

### 29.6 REST API 요청·응답 계약

| HTTP | Path | 로그인 필요 | 비고 |
|---|---|---|---|
| GET | `/api/v1/auth/csrf` | 아니오 | CSRF 초기화, Set-Cookie 또는 토큰 응답 정책 통일 |
| POST | `/api/v1/auth/signup` | 아니오 | 초대 전용/공개 가입 정책과 유효성 검증 적용 |
| POST | `/api/v1/auth/login` | 아니오 | 자격증명 검사, 세션 교체, 쿠키 응답 |
| GET | `/api/v1/auth/session` | 아니오 | 로그인 시 200, 비로그인 시 401 |
| POST | `/api/v1/auth/logout` | 예 | 세션 무효화 및 쿠키 만료 |
| GET | `/api/v1/users/me` | 예 | 사용자 프로필·온보딩 진행 여부 |
| PATCH | `/api/v1/users/me/settings` | 예 | 시간대/통화/표시 이름 변경 |
| POST | `/api/v1/auth/password/forgot` | 아니오 | M1: 존재 여부 상관없이 동일 메시지·유사 응답시간 |
| POST | `/api/v1/auth/password/reset` | 아니오 | M1: 유효·미사용 토큰으로 비밀번호 변경 |
| POST | `/api/v1/auth/sessions/revoke-all` | 예 + 재인증 | M1: 전체 세션 철회 |

**로그인 요청 예시 — JSON 로그인 핸들러를 실제 구현하는 경우**

```http
POST /api/v1/auth/login
Content-Type: application/json
X-CSRF-TOKEN: <서버에서 발급한 CSRF 토큰>

{
  "email": "person@example.com",
  "password": "사용자가 입력한 비밀번호"
}
```

**성공 응답 예시**

```http
HTTP/1.1 200 OK
Set-Cookie: MT_SESSION=<opaque-session-id>; Path=/; HttpOnly; Secure; SameSite=Lax
Cache-Control: no-store
Content-Type: application/json

{
  "user": {
    "id": "48b2d3f5-09c2-4620-989a-1d1808d6f935",
    "displayName": "사용자",
    "timezone": "Asia/Seoul",
    "baseCurrency": "KRW"
  },
  "onboardingCompleted": false
}
```

**실패 응답 예시**

```http
HTTP/1.1 401 Unauthorized
Content-Type: application/problem+json
Cache-Control: no-store

{
  "type": "about:blank",
  "title": "Unauthorized",
  "status": 401,
  "code": "INVALID_CREDENTIALS",
  "message": "이메일 또는 비밀번호를 확인해 주세요."
}
```

- 공개 회원가입 API는 가입 허용 여부/이메일 검증 상태에 따라 `201` 또는 `202` 등의 계약을 확정한다.
- `429 TOO_MANY_REQUESTS`는 요청 제한 오류. `Retry-After` 헤더를 사용할 수 있다.
- 실사용 API 실패 응답에 입력된 비밀번호, 해시, 복구 토큰, 세션 ID를 포함하지 않는다.
- 로그인 성공 시 클라이언트 쿠키를 직접 읽지 않고 `credentials: 'include'` 등의 브라우저 쿠키 전송 옵션을 사용한다(동일 출처 정책과 일치시킨다).

### 29.7 프런트엔드 구현 포인트

**파일 구성 제안**

```text
apps/web/src/
  pages/
    WelcomePage.vue
    LoginPage.vue
    SignupPage.vue
    ForgotPasswordPage.vue       # M1
    ResetPasswordPage.vue        # M1
    OnboardingPage.vue
    TodayPage.vue
  router/
    routes.ts
    authGuard.ts
  stores/
    auth.ts                     # 로그인/세션/프로필 최소 상태
  services/
    authApi.ts                  # CSRF/login/logout/session
  layouts/
    PublicLayout.vue            # 로그인 전
    AuthenticatedLayout.vue     # 로그인 후
```

**Route Guard 의사코드**

```ts
async function beforeEach(to) {
  const auth = useAuthStore();
  const result = await auth.ensureSessionChecked(); // /auth/session, 200/401/error 구별
  if (result === 'network-error') return { name: 'network-error' };
  if (to.meta.requiresAuth && result === 'unauthenticated') {
    const path = to.fullPath;
    return { path: '/login', query: { next: isSafeInternalPath(path) ? path : '/today' } };
  }
  if (result === 'authenticated' && to.meta.guestOnly) {
    return { path: auth.onboarded ? '/today' : '/onboarding' };
  }
  if (result === 'authenticated' && !auth.onboarded && to.path !== '/onboarding') {
    return { path: '/onboarding' };
  }
  return true;
}
```

**실제 구현 시:** 모든 라우트에 `meta` 유형을 명시하고 공개 정적 화면에서 불필요한 서버 세션 호출을 하지 않도록 상태 초기화 횟수를 관리한다. 무한 리다이렉트를 막기 위한 `/onboarding`의 특수 처리를 포함한다.

### 29.8 무료 운영 및 플랫폼별 보안·개인정보 원칙

- **비용 0원 우선:** 로그인은 자체 DB + Spring Security로 구현하여 Google·Apple 유료 계정 구매 없이 동작하도록 한다.
- 이메일 발송은 자동으로 무료가 되는 것이 아니다. SMTP/메일 발송량/도메인 소유 확인을 따로 검토하고, 첫 개인 베타는 초대/사전 발급 계정으로 운영한다.
- iOS PWA는 Safari 홈 화면에 추가하여 실행하고 Android는 Chrome 홈 화면 설치를 사용한다. **탈옥, IPA 사이드로딩, 시스템 설정 우회가 필요 없다.**
- 동일 계정 로그인을 쓰더라도 각 기기의 세션은 독립이며, 로그아웃은 기본적으로 현재 기기에서만 한다.
- PWA/웹에서 금융 기록을 다루므로 화면 자동 완성·최근 앱 미리보기·공용 기기 로그아웃 UX 등을 검토한다.
- 네이티브 앱으로 확장할 때는 Capacitor+시스템 브라우저 인증/OS 보안 저장소를 별도 설계한다. 현재 PWA용 세션 모델을 강제로 변경하지 않는다.
- 무료 호스팅/무료 시세 API는 용량·라이선스·가용성 제한이 있을 수 있으므로 **서비스 운영 전체가 항상 무료임을 보장하지 않는다**.

### 29.9 수용 기준 (QA 시나리오)

- [ ] 첫 방문 시 로그인·회원가입 선택이 보이고 개인 홈/가계부 내용은 보이지 않는다.
- [ ] 비로그인 사용자가 `/today`, `/money`, `/invest` URL을 직접 열어도 보호 화면과 개인 API가 모두 차단된다.
- [ ] 유효한 사용자 로그인 성공 → 최초 1회 온보딩 → `/today` 표시.
- [ ] 이미 온보딩한 사용자 재로그인 시 온보딩 반복 강제 없음.
- [ ] 동일 계정으로 PC/iPhone/Android PWA에서 로그인하고 동일한 할 일과 지출을 조회할 수 있다.
- [ ] 두 사용자 로그인 시 일정·할 일·금융·투자 데이터가 섞이지 않는다.
- [ ] 세션 만료 후 개인 API `401`, 클라이언트 로그인 이동 및 데이터 초기화가 정상 작동한다.
- [ ] 로그아웃 후 뒤로가기·새로고침·오프라인 재진입 시 다른 사용자 데이터가 표시되지 않는다.
- [ ] CSRF 토큰이 없는 상태 변경 요청과 다른 출처 악의적 요청이 차단된다.
- [ ] 로그인 실패, 가입 중복, 잘못된 이메일, 속도 제한, 네트워크 오류 메시지가 구별되며 사용자 열거를 유발하지 않는다.
- [ ] 비밀번호 평문이나 세션 값이 로그·DB 평문 인증 컬럼·프런트 localStorage에 남지 않는다.
- [ ] 복구 토큰은 만료/재사용/무차별 대입에 대응하며 계정 존재 여부를 노출하지 않는다(M1).
- [ ] 공개 회원가입을 열 경우 이메일 검증 후에만 활성화되고 재설정 메일 발송이 동작한다.
- [ ] iPhone Safari(홈 화면 추가 포함)와 Android Chrome에서 키보드, 입력 포커스, Safe Area, PWA 재실행을 테스트한다.

### 29.10 개발 티켓 분할 및 수순

| 순서 | 이슈 | 작업 | 완료 조건 |
|---|---|---|---|
| 1 | AUTH-001 | PublicLayout, Welcome/Login/Signup, Router Guard | 비인증 첫 화면·보호 경로 UX 완성 |
| 2 | AUTH-002 | LocalCredentials, PasswordEncoder, Spring Security, 세션·CSRF | 회원가입·로그인·로그아웃 동작 및 보안 테스트 |
| 3 | AUTH-003 | 온보딩, 프로필, 시간대·통화 | 로그인 최초 1회 설정 후 오늘 홈 진입 |
| 4 | AUTH-004 | IDOR 방지·입력 검증·속도 제한·보안 이벤트 | 두 계정 격리·무차별 대입 테스트 통과 |
| 5 | AUTH-005 | SMTP, 이메일 검증, 비밀번호 재설정 | 공개 가입 전 필수 게이트 충족(M1) |
| 6 | AUTH-006 | 세션 목록·전체 로그아웃, 재인증 | 분실기기 세션 철회 검증(M1) |
| 7 | AUTH-007 | Google OIDC/Passkey 선택 확장 | 인증 방식 변경 시 계정 연결 안전성 테스트(M2) |

**고정 UX 결정:** 제품의 첫 화면은 개인 대시보드가 아니라 **로그인 게이트**이다. 로그인/회원가입 이후 온보딩을 거쳐 오늘 대시보드로 진입한다. 재방문 시 세션이 유효하면 다시 로그인할 필요가 없다.

---

## 부록 A. 1분 사용 시나리오(출시 UX 검증)

```text
앱 열기 → 유효 세션 확인
  ↓
세션 없음: 로그인/회원가입 → 첫 설정(최초 1회)
  ↓
오늘 일정 / 할 일 / 예산 / 주가를 한 화면에서 파악
  ↓
[+] 버튼 터치 → 지출 선택 → 9,500원 입력 → 저장
  ↓
홈 오늘 지출 및 예산 잔액이 즉시 변경
  ↓
할 일 하나 완료 → 실제 수행 40분 선택 입력
  ↓
성장 카드에 40분 1회만 반영
  ↓
앱 닫기
```

이 흐름이 **불필요한 화면 이동이나 중복 입력 없이** 실행되면 첫 번째 제품 가치가 실현되었다고 본다.

## 부록 B. 문서 우선순위·변경 규칙

- 이 문서의 핵심 불변식(원장·중복 방지·보안·시세 사용권)은 UI 편의보다 우선한다.
- 애매한 요구사항은 `docs/ADR/*.md`에 선택지, 결정, 근거, 영향을 기록한다.
- 개발 중 추가되는 기능에는 `기능 ID`, `정책`, `테이블`, `API`, `테스트`, `화면`을 연결해 추적 가능하게 한다.
- 기존 저장소 README는 제품 소개용으로 짧게 유지하고 이 문서가 상세 명세를 맡는다.
- 명세에 나온 API 예시·SQL·디렉터리 구조는 구현 착수 기준 초안이며 **프로덕션 코드 검증을 대체하지 않는다**.

**끝. v1.2.0 — 무료 PWA 우선·최초 로그인·회원가입·온보딩·인증 보안 반영.**
