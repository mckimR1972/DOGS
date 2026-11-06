# DOGS 팀 개발 기준

기준일: 2026-11-06. 사용자가 전달한 최신 팀 기능·역할·협업 규칙을 정리한 문서다. **목표 명세이며 구현 완료 목록이 아니다.** 현재 구현 상태는 [README](README.md), 현재 DB와 이전 과제는 [BACKEND_CONTRACT](BACKEND_CONTRACT.md)를 확인한다. 이전 제안과 충돌하면 이 팀 기준을 우선하며, 세부사항이 없는 부분은 임의로 확정하지 않는다.

## 핵심 개념

- 데이터의 기준은 그룹이다. 반려견은 그룹에 등록되고 그룹에 종속된다.
- 같은 그룹의 멤버는 반려견 정보, ToDo, 사진첩을 공유한다.
- 그룹 밖 사용자는 그룹 데이터를 조회·수정할 수 없다.
- ToDo 생성·수정·삭제는 그룹장이 담당하고 멤버는 변경을 제안한다.

## 필수 기능

| 영역 | 요구사항 |
|---|---|
| 계정 | 회원가입·로그인·로그아웃, 이름·사용자 프로필 사진 관리 |
| 그룹 | 생성자가 그룹장, 초대 코드 초대·가입, 탈퇴·그룹장 위임, 외부 접근 차단 |
| 반려견 | 그룹 내 등록·조회·수정·삭제, 이름·견종·생일·체중·사진 |
| ToDo | 그룹장 생성·수정·삭제, 매일·요일별 반복, 완료자·완료 시각 기록, 반려견 지정은 선택 |
| 변경 제안 | 멤버 제안 → 그룹장 승인/거절 → 승인 시 ToDo 반영 → 제안자에게 결과 알림 |
| 요청 | 멤버에게 요청 전송, 수락/거절, 알림 전달 |
| 공용 사진첩 | 그룹 공용 업로드·조회·삭제, 반려견 태그 |
| 공통 알림 | 앱 내 알림함·읽음 상태와 푸시, ToDo 생성/변경·제안/결과·요청·ToDo 시간 도래 |

전원 승인 방식은 사용하지 않는다. 반려견·ToDo·사진·제안·요청 데이터로 CRUD 요구사항을 충족한다. ToDo 완료는 중복 급여·누락을 방지할 수 있도록 기록한다.

## 권한

아래 권한은 모두 **현재 그룹에 속한 사용자**를 전제로 한다. UI 버튼 숨김만으로 끝내지 않고 서버에서도 강제해야 한다.

| 행동 | 그룹장 | 멤버 | 그룹 밖 사용자 |
|---|---|---|---|
| 그룹 데이터 조회 | 가능 | 가능 | 불가 |
| 반려견 등록·수정 | 가능 | 가능 | 불가 |
| ToDo 생성·수정·삭제 | 가능 | 불가, 변경 제안 | 불가 |
| ToDo 완료 체크 | 가능 | 가능 | 불가 |
| 변경 제안 승인·거절 | 가능 | 불가 | 불가 |
| 사진 업로드 | 가능 | 가능 | 불가 |
| 멤버 초대·그룹장 위임 | 가능 | 불가 | 불가 |
| 사진 삭제 | 가능 | 본인이 올린 사진만 | 불가 |

사진 삭제는 앞서 사용자와 정한 정책을 함께 기록했다. 그룹 밖 접근 금지 기준에 따라 탈퇴자는 직접 사진에 접근하지 않는다. 탈퇴 시 기존 사진을 유지할지 삭제할지, 삭제 요청 절차를 둘지는 미정이다. 반려견 삭제 권한, 그룹장 탈퇴의 선행 조건, 마지막 멤버 탈퇴 시 데이터 처리도 추가 결정이 필요하다.

## 논리 데이터 모델

아래는 팀이 제시한 엔터티와 필드다. **Firestore 컬렉션 경로·필드 타입·인덱스가 확정된 물리 스키마는 아니다.** 사용자 ID는 Firebase Auth uid와 연결한다. `?`는 선택 항목이다.

```text
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

모든 그룹 데이터 접근은 GroupMember 소속 여부를 검증한다. TodoLog·ChangeRequest처럼 group_id가 직접 없는 모델은 상위 ToDo의 그룹까지 확인해야 한다. 구체적인 구현 경로는 서버/DB 담당과 앱 로직 담당이 맞춘다.

구현 전 정할 세부 계약:

- 반복 ToDo의 날짜별 회차 식별자·시간대·중복 완료 처리: todo_id만으로는 매일 반복되는 서로 다른 회차를 구분할 수 없다.
- 제안 승인과 ToDo 변경의 일관성, 이미 변경된 ToDo에 대한 제안 충돌 처리.
- 초대 코드 만료·재발급·가입 검증, 역할 변경·그룹장 위임의 신뢰 가능한 처리 주체.
- 사진 태그 단일/복수 여부: 현재 팀 모델은 선택적인 dog_id 한 개다. 복수 태그는 확정되지 않았다.
- 알림 이벤트의 중복 생성 방지, 수신 대상·기기 토큰 관리·읽음 권한.
- 개인 초기 설정을 그룹 데이터로 옮기는 시점과 중복 생성 방지.

## 기술 스택

| 영역 | 팀 기준 |
|---|---|
| 언어·플랫폼 | Kotlin, Android / Android Studio |
| UI | Jetpack Compose |
| 아키텍처 | MVVM, ViewModel·StateFlow·Coroutines |
| 이미지 로딩 | Coil |
| 인증·DB | Firebase Authentication, Cloud Firestore |
| 파일 저장 | Firebase Storage |
| 서버 처리 | Cloud Functions: 알림 트리거, 변경 제안 승인 등 |
| 푸시 | Firebase Cloud Messaging (FCM) |
| 확장 AI | LLM API |
| 협업 | GitHub, GitHub Issues |

Firebase Storage가 현재 팀 명세의 기본이다. 이전 Supabase 논의는 비용 대안 검토이며 스택 변경으로 확정된 것이 아니다. Storage·Functions의 도입 비용과 요금제는 도입 전에 확인한다. **이 문서 반영으로 과금 설정·서비스 연결을 수행하지 않는다.** Coil·Storage·FCM·Functions도 현재 구현 완료를 의미하지 않는다.

외부 API(LLM 등)는 확장 기능에만 사용하고 핵심 앱 로직은 직접 구현한다. 인증·DB 등 명시된 Firebase 기반 서비스는 팀 기술 스택에 따른다.

## 역할과 연결 지점

| 역할 | 팀 기준 담당 |
|---|---|
| PM | 일정·이슈·의견 조율·종합·발표, 일부 건강 상식 팁·AI 도우미 개발 |
| 서버/DB | Firestore 스키마·그룹 소속 보안 규칙·Cloud Functions 알림 트리거와 제안 승인 처리 |
| 백엔드(앱 로직) | Firebase 인증·Storage 사진 저장·FCM 수신·알림함·Repository·ViewModel |
| 프론트엔드 | Compose 화면·UI/UX·Navigation |

이 대화의 사용자 담당은 백엔드(앱 로직)다. 프론트엔드는 화면 구현에 집중하도록 Repository·ViewModel까지 앱 로직 담당이 제공한다. 그룹·ToDo 등 기능별 클래스의 세부 분담은 Issue에서 명시한다.

서버/DB 담당과 앱 로직 담당은 먼저 데이터 경로·필드·권한·오류·호출 계약을 맞춘다. 프론트엔드에는 화면 상태, 사용자 동작 함수, 로딩·실패 상태를 전달한다. 임시 UI는 교체 가능한 테스트 화면으로 공유한다.

## 확장 범위와 일정

확장 우선순위는 1) 강아지 건강 상식 팁 2) 그룹 기록을 조회·요약하고 질문에 답하는 AI 도우미다. 그룹 간 커뮤니티와 홈캠은 보류한다.

| 주차 | 팀 일정(2026년) | 내용 |
|---|---|---|
| W9 | 11/03 | 제안 발표 |
| W10 | ~11/10 | 로그인·그룹·반려견 프로필 |
| W11 | ~11/17 | ToDo 반복·완료·권한 |
| W12 | ~11/24 | 변경 제안·요청·알림 |
| W13 | ~12/01 | 사진첩·확장 기능·UI 다듬기 |
| W14·15 | 12/08, 12/15 | 데모 발표 |

## 협업 규칙

- main: 배포·제출용, 직접 push 금지.
- dev: 통합 브랜치.
- feature/<기능명>: dev에서 생성하는 기능 브랜치.
- 기능 단위 GitHub Issue → feature 개발 → dev 대상 PR → 팀원 1명 이상 리뷰 후 merge.
- 제출할 버전은 dev에서 확인한 뒤 main 대상으로 PR을 만들어 반영한다.
- 커밋 예: `feat: 그룹 초대 코드 기능 추가`, `fix: ToDo 완료 체크 중복 오류 수정`, `docs: README 수정`.

팀 원격 저장소의 기존 main 이력을 기준으로 dev를 구성한다. 문서에 적는 것과 GitHub에서 브랜치 보호를 설정하는 것은 별개이며, 보호 설정·Issue·PR을 이미 생성한 상태로 취급하지 않는다.
