# DOGS 🐶

> 모바일 앱 개발 팀 프로젝트 (2026 Fall) — 가족·친구가 **그룹 단위로 함께 돌보는** 반려동물 관리 앱

여러 사람이 한 마리(또는 여러 마리)의 반려견을 함께 돌볼 때, "밥 줬어?", "산책 누가 갔어?" 같은 확인이 반복되고 중복 급여나 누락이 생깁니다.
DOGS는 **그룹**을 중심으로 반려견 정보, 할 일(ToDo), 사진첩, 알림을 공유하여 누가 언제 무엇을 했는지 한눈에 볼 수 있게 합니다.

---

## 목차

1. [핵심 개념](#1-핵심-개념)
2. [필수 기능](#2-필수-기능)
3. [확장 기능](#3-확장-기능)
4. [권한 정책](#4-권한-정책)
5. [데이터 모델](#5-데이터-모델)
6. [기술 스택](#6-기술-스택)
7. [팀 구성 및 역할](#7-팀-구성-및-역할)
8. [일정](#8-일정)
9. [협업 규칙](#9-협업-규칙)
10. [현재 구현 상태](#10-현재-구현-상태)
11. [개발 환경 및 실행 방법](#11-개발-환경-및-실행-방법)
12. [검증 방법](#12-검증-방법)


---

## 1. 핵심 개념

- **그룹 기준 설계** — 데이터의 기준은 개가 아니라 **그룹**입니다. 반려견은 그룹에 등록되며 그룹에 종속됩니다.
- **그룹 단위 공유** — 같은 그룹의 멤버(예: P1, P2, P3)는 반려견 정보, ToDo, 사진첩을 공유합니다.
- **접근 제한** — 그룹에 속하지 않은 사용자(예: P4)는 해당 그룹의 데이터를 조회하거나 수정할 수 없습니다.
- **그룹장 중심 관리** — ToDo 생성·수정은 그룹장이 담당하고, 멤버는 변경을 **제안**합니다.

---

## 2. 필수 기능

### 2.1 계정
- 회원가입 / 로그인 / 로그아웃
- 사용자 기본 정보 관리 (이름, 프로필 사진)

### 2.2 그룹
- 그룹 생성 (생성자가 그룹장)
- 초대 코드로 멤버 초대·가입
- 그룹 탈퇴, 그룹장 위임
- 그룹 밖 사용자 접근 차단

### 2.3 반려견 프로필
- 그룹에 반려견 등록 / 조회 / 수정 / 삭제
- 이름, 견종, 생일, 체중, 사진

### 2.4 ToDo
- 그룹장이 ToDo 생성·수정·삭제 (예: 아침 급여, 저녁 산책, 약 먹이기)
- **반복 일정** 지원 (매일, 요일별 등)
- **완료 체크** — 누가, 언제 완료했는지 기록 → 중복 급여·누락 방지
- 반려견별 ToDo 지정 (선택)

### 2.5 변경 제안
- 멤버가 ToDo 변경 내용을 제안
- 그룹장이 승인 또는 거절
- 제안·결과 알림

### 2.6 요청
- 멤버에게 요청 보내기 (예: "오늘 산책 부탁해")
- 수락 / 거절, 알림으로 전달

### 2.7 공용 사진첩
- 그룹 공용 사진 업로드 / 조회 / 삭제
- 사진에 반려견 태그

### 2.8 알림
- 하나의 공통 알림 시스템으로 통합
  - 앱 내 알림함 (읽음 여부 관리)
  - 푸시 알림
- 알림 대상 이벤트: ToDo 생성·변경, 변경 제안·처리 결과, 요청, ToDo 시간 도래

> **Data CRUD 요구사항**은 반려견, ToDo, 사진, 제안·요청 데이터를 통해 충족합니다.

---

## 3. 확장 기능

시간이 허락하는 범위에서 우선순위 순으로 진행합니다.

| 우선순위 | 기능 | 설명 |
|:---:|---|---|
| 1 | 강아지 건강 상식 팁 | 홈 화면 등에 짧은 건강 상식을 주기적으로 표시 |
| 2 | AI 도우미 (챗봇) | 그룹에 쌓인 기록(ToDo 완료 이력, 체중 등)을 조회·요약하고 반려견 관련 질문에 답변 |
| - | 그룹 간 커뮤니티 | 보류 (범위 초과) |
| - | 홈캠 연동 | 보류 (하드웨어·스트리밍 필요) |

> 외부 API(LLM 등)는 확장 기능에만 사용하며, 앱의 핵심 로직은 직접 구현합니다.

---

## 4. 권한 정책

| 행동 | 그룹장 | 멤버 | 그룹 외 사용자 |
|---|:---:|:---:|:---:|
| 그룹 데이터 조회 | ✅ | ✅ | ❌ |
| 반려견 등록·수정 | ✅ | ✅ | ❌ |
| ToDo 생성·수정·삭제 | ✅ | ❌ (제안만 가능) | ❌ |
| ToDo 완료 체크 | ✅ | ✅ | ❌ |
| 변경 제안 승인·거절 | ✅ | ❌ | ❌ |
| 사진 업로드 | ✅ | ✅ | ❌ |
| 멤버 초대·그룹장 위임 | ✅ | ❌ | ❌ |

**변경 제안 흐름**

```
멤버가 변경 제안 작성 → 그룹장에게 알림 → 그룹장 승인/거절 → ToDo 반영 + 제안자에게 결과 알림
```

> 전원 승인 방식은 멤버 한 명이 응답하지 않으면 변경이 무기한 대기되므로 채택하지 않았습니다.

---

## 5. 데이터 모델

```
User          (id, email, name, profile_img)
Group         (id, name, leader_id, invite_code)
GroupMember   (group_id, user_id, role, joined_at)
Dog           (id, group_id, name, breed, birth, weight, photo)
Todo          (id, group_id, dog_id?, title, repeat_rule, due_at, created_by)
TodoLog       (id, todo_id, done_by, done_at)
ChangeRequest (id, todo_id, proposer_id, payload, status)
Request       (id, group_id, from_user_id, to_user_id, content, status)
Photo         (id, group_id, dog_id?, uploader_id, url, created_at)
Notification  (id, user_id, type, ref_id, is_read, created_at)
```

- 모든 그룹 데이터 접근 시 `GroupMember` 소속 여부를 먼저 확인합니다.
- `?` 표시는 선택 항목입니다.

---

## 6. 기술 스택

> 앱은 **Kotlin(Android)** 으로 개발합니다.

| 영역 | 기술 |
|---|---|
| 언어 | Kotlin |
| 플랫폼 | Android (Android Studio) |
| UI | Jetpack Compose |
| 아키텍처 | MVVM (ViewModel, StateFlow, Coroutines) |
| 이미지 로딩 | Coil |
| 인증 | Firebase Authentication |
| DB | Cloud Firestore |
| 파일 저장 | Firebase Storage |
| 푸시 알림 | Firebase Cloud Messaging (FCM) |
| AI (확장) | LLM API |
| 협업 | GitHub, GitHub Issues |

---

## 7. 팀 구성 및 역할

> 4인 구성 — PM 1, 서버/DB 1, 백엔드 1, 프론트엔드 1

| 이름 | 역할 | 담당 |
|---|---|---|
| | PM | 일정·이슈 관리, 의견 조율 및 종합, 발표 총괄 / 일부 기능 개발 (건강 상식 팁, AI 도우미 등) |
| | 서버/DB | Firestore 스키마 설계, 보안 규칙(그룹 소속 확인), Cloud Functions(알림 트리거, 변경 제안 승인 처리) |
| | 백엔드 (앱 로직) | Firebase 인증, 사진 저장(Storage), FCM 수신·알림함, Repository·ViewModel |
| | 프론트엔드 | Jetpack Compose 화면 구현, UI/UX 디자인, 화면 흐름(Navigation) |

> Firebase를 사용하므로 별도 서버 대신 Cloud Functions가 서버 역할을 합니다. 프론트엔드가 1명이므로 백엔드 담당이 Repository와 ViewModel까지 맡아, 프론트엔드는 화면 구현에 집중합니다.

---

## 8. 일정

| 주차 | 날짜 | 내용 |
|---|---|---|
| W9 | 11/03 | 프로젝트 제안 발표 |
| W10 | ~11/10 | 로그인, 그룹, 반려견 프로필 |
| W11 | ~11/17 | ToDo (반복, 완료 체크), 권한 |
| W12 | ~11/24 | 변경 제안, 요청, 알림 |
| W13 | ~12/01 | 사진첩, 확장 기능, UI 다듬기 |
| W14·15 | 12/08, 12/15 | 프로젝트 데모 발표 |

---

## 9. 협업 규칙

### 브랜치
- `main` — 배포·제출용, 직접 push 금지
- `dev` — 통합 브랜치
- `feature/<기능명>` — 기능 개발 (예: `feature/group-invite`)

### 작업 흐름
1. 기능 단위로 GitHub Issue 생성
2. `dev`에서 `feature/*` 브랜치 생성
3. 작업 후 `dev`로 Pull Request, 팀원 1명 이상 리뷰 후 merge

### 커밋 메시지
```
feat: 그룹 초대 코드 기능 추가
fix: ToDo 완료 체크 중복 오류 수정
docs: README 수정
```

---

## 10. 현재 구현 상태

팀에서 합의한 그룹 중심 모델로 구현합니다. 1~9번은 목표 기능과 협업 기준이며, 아래는 현재 앱에서 확인할 수 있는 범위입니다.


| 기능 | 상태 |
|---|---|
| Google 로그인·로그아웃·세션 유지 | Firebase Authentication 연결 |
| 초기 설정 | 실제 1인 그룹·그룹장·반려견·초기 ToDo 생성 |
| 반려견 이름·견종·생일·체중 수정 | 그룹 문서에 저장 |
| ToDo 추가·날짜별 완료/취소 | 그룹 문서·완료자·서버 시각 저장, 권한 확인 |
| 그룹 데이터 조회 | 새로고침·앱 복귀 시 서버 조회, 실시간 구독 미구현 |
| 사진 선택·표시 | 기기 저장, Storage 업로드 미구현 |
| 제안·요청·알림 | 데이터 모델·일부 Repository/규칙 준비, UI·승인·FCM 미구현 |
| 초대 가입·위임·탈퇴 | 미구현, 임의 멤버십 변경 차단 |
| 샘플 체험 | 기기 전용 임시 UI |

### 로그인 후 화면 흐름

```text
Google 로그인 → 사용자·소속 그룹 서버 조회
  → 그룹이 있으면 홈
  → 신규 사용자이면 초기 설정 → 그룹·그룹장·반려견·초기 할 일 저장 → 홈
```

그룹 데이터는 소속 멤버만 접근합니다. 초대 가입은 아직 미구현이므로 같은 Firebase 프로젝트를 사용하더라도 다른 계정이 자동으로 같은 그룹에 들어가지는 않습니다.

---

## 11. 개발 환경 및 실행 방법


현재 저장소 설정 기준이며 최신 버전으로 임의 변경하지 않습니다.

| 항목 | 설정 |
|---|---|
| Android 패키지 | `com.teamdogs.dogs` |
| 최소 Android API | 26 |
| compileSdk / targetSdk | 37 / 37 |
| Android Gradle Plugin | 9.3.3 |
| Gradle Wrapper | 9.5.0 |
| Kotlin Compose 플러그인 | 2.2.10 |
| Firebase BoM | 34.7.0 |
| Gradle daemon JVM | 25 (`gradle/gradle-daemon-jvm.properties`) |
| Java 소스/타깃 호환성 | 11 — Gradle 실행 JVM 버전과 별개 |

프로젝트의 AGP/SDK를 지원하는 Android Studio를 사용합니다. Gradle Wrapper와 JVM toolchain 설정을 공유하므로 팀원이 같은 설정으로 빌드해야 합니다. 최초 동기화 때 Gradle/JDK/라이브러리 다운로드가 필요할 수 있습니다.

### 처음 내려받은 팀원의 실행 순서

1. 저장소를 clone한 뒤 Android Studio에서 `settings.gradle.kts`가 있는 최상위 폴더를 엽니다.
2. SDK Manager에서 Android SDK Platform 37을 준비하고 프로젝트 Gradle 설정으로 동기화합니다. `local.properties`의 SDK 경로는 각자 PC 환경에 맞게 IDE가 생성하도록 둡니다.
3. 팀 Firebase 프로젝트의 Android 앱 설정 파일을 받아 **`app/google-services.json`**에 넣습니다.
4. Google 로그인을 테스트할 개발자는 자신의 디버그 서명 지문을 확인하여 같은 Firebase Android 앱에 추가합니다. 필요하면 갱신된 설정 파일을 다시 받습니다.
5. Google Play 서비스가 있는 에뮬레이터 또는 실제 기기에서 `app` Debug 구성을 실행합니다.

```sh
./gradlew :app:signingReport
```

Windows PowerShell에서는 `./gradlew` 대신 `.\gradlew.bat`를 사용합니다. 현재 프로젝트 ID는 `dogs-dev-f9a4e`입니다. 팀이 다른 공용 프로젝트로 옮기기로 했다면 설정 파일과 보안 규칙 배포 대상을 함께 변경해야 합니다.

`google-services.json`은 관리자 비밀키가 아니라 앱의 Firebase 연결 설정입니다. 이 저장소에서는 프로젝트 설정을 별도 전달하기 위해 Git에서 제외합니다. 저장소만 clone하면 이 파일이 없으므로 **샘플 화면만 실행하더라도 빌드 전에 파일을 준비해야 합니다.** [Firebase 설정 파일 안내](https://firebase.google.com/docs/android/setup)

Firebase 콘솔 접근이 필요한 개발자는 본인 Google 계정으로 프로젝트 권한을 받습니다. 앱에서의 가족 그룹 권한과 콘솔 관리 권한은 서로 다릅니다. 서비스 계정 비밀키, 실제 사용자 토큰, 릴리스 서명키는 앱 소스에 넣지 않습니다.

### 참고 문서

- [그룹 스키마](GROUP_SCHEMA.md): 실제 경로·필드·권한
- [백엔드 협업 기준](BACKEND_CONTRACT.md): 담당 범위와 인터페이스
- [Firestore 설정](FIRESTORE_SETUP.md): 서버 설정·로컬 테스트
- [화면 테스트](PROTOTYPE_TESTING.md): 실제 로그인과 샘플 체험

---

## 12. 검증 방법

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
```

APK 빌드와 기기 테스트 실행은 별도입니다. Firebase 에뮬레이터에서 그룹 생성·재시도·완료 체크·역할별 권한을 검증합니다. 실행 명령은 [Firestore 설정](FIRESTORE_SETUP.md)을 참고합니다.

실제 계정의 그룹 데이터 서버 조회와 홈 복원을 확인했습니다. Storage 업로드·FCM 발송·초대 가입은 아직 구현되지 않았습니다. 전체 화면 회귀와 실제 두 번째 기기 동기화는 별도 검증 대상입니다.