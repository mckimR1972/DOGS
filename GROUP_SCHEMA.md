# 그룹 기준 Firestore 스키마

2026-11-06. 팀 논리 모델을 앱 코드와 Firestore 경로에 연결한 현재 구현 계약이다. 기능 전체 완성과 구분한다. 초기 사진 업로드·그룹 가입/위임·제안 승인/알림 생성 서버 처리는 아직 연결하지 않았다.

## 경로

```text
users/{uid}
users/{uid}/notifications/{notificationId}
groups/{groupId}
groups/{groupId}/members/{uid}
groups/{groupId}/dogs/{dogId}
groups/{groupId}/todos/{todoId}
groups/{groupId}/todoLogs/{todoId}_{YYYY-MM-DD}
groups/{groupId}/changeRequests/{changeRequestId}
groups/{groupId}/requests/{requestId}
groups/{groupId}/photos/{photoId}
```

현재 실제로 작성하는 컬렉션은 users, groups, members, dogs, todos와 완료 시 todoLogs다. 나머지 빈 컬렉션은 콘솔에 미리 생성하지 않는다. 데이터가 생성될 때 나타난다.

## 모델과 필드

Kotlin 모델은 `app/src/main/java/com/teamdogs/dogs/group/Models.kt`다. 필드 이름은 팀 명세의 snake_case를 그대로 사용한다. 데이터 클래스의 기본값은 역직렬화를 위한 것이며 허용 여부는 보안 규칙이 검증한다.

| 모델 | 필드·형식 |
|---|---|
| User | id=Auth uid, email/name/profile_img 문자열, active_group_id 선택 문자열, schema_version=2 |
| Group | id/name/leader_id 문자열, invite_code=null(아직 비활성), timezone=Asia/Seoul, created_at 서버 Timestamp |
| GroupMember | group_id/user_id 문자열, role=leader 또는 member, joined_at 서버 Timestamp |
| Dog | id/group_id/name/breed/photo 문자열, birth 선택 YYYY-MM-DD, weight 선택 kg 숫자 |
| Todo | id/group_id/title/created_by/note 문자열, dog_id 선택 문자열, repeat_rule 맵, due_at HH:mm 문자열 |
| TodoLog | id/todo_id/done_by/occurrence_day 문자열, done_at 서버 Timestamp |
| ChangeRequest | id/todo_id/proposer_id 문자열, payload 맵, status=pending |
| CareRequest (팀 Request) | id/group_id/from_user_id/to_user_id/content 문자열, status=pending/accepted/rejected |
| Photo | id/group_id/uploader_id/url/storage_path 문자열, dog_id 선택 문자열, created_at Timestamp, status |
| Notification | id/user_id/type/ref_id 문자열, is_read boolean, created_at Timestamp |

- repeat_rule은 `{frequency: daily, weekdays: []}` 또는 `{frequency: weekly, weekdays: [1,3,5]}`다. 1=월요일, 7=일요일.
- due_at은 반복 일정의 현지 시간이다. 단일 절대 Timestamp가 아니다. 날짜는 그룹의 Asia/Seoul 시간대로 계산한다. 임의 시간대/일회성 일정은 후속 확장이다.
- dog_id=null이면 특정 반려견에 속하지 않는 그룹 공통 ToDo다.
- TodoLog 문서 ID는 ToDo ID + 회차 날짜로 고정한다. 같은 회차를 다시 완료해도 기록을 덮어쓰지 않는다. 완료 취소는 완료자 또는 그룹장만 가능하며 취소 후 다시 완료하면 새 완료 기록이 된다.
- 날짜는 현재 앱에서 계산한다. 규칙은 날짜 문자열 형식·문서 ID 일치를 검사하며 서버 날짜/요일과의 일치까지 강제하지 않는다. 변조된 날짜 입력까지 제한하려면 후속 회차 검증이 필요하다.
- photo/profile_img/url은 서버 파일 참조를 위한 필드다. 기기 절대 경로를 이 필드에 저장하지 않는다. 현재 사진 서버 쓰기는 차단되어 있다.
- 표시 이름과 ID를 구분한다. 그룹원 이름 조회는 별도 공개 프로필 계약이 아직 없으므로 다른 멤버는 uid 일부로 표시하며 users 전체 읽기를 열지 않는다.

## 현재 접근 규칙

| 대상 | 허용 범위 |
|---|---|
| 사용자 프로필 | 본인 단건 읽기·쓰기. active_group_id는 본인이 속한 그룹 또는 null |
| 그룹 | 멤버 읽기. 최초 생성은 본인을 그룹장으로 user/group/member와 함께 원자적 생성. 이름 변경은 그룹장 |
| 멤버 | 같은 그룹 읽기. 최초 그룹장 문서 외 일반 클라이언트 생성/역할 수정/삭제 차단 |
| 반려견 | 같은 그룹 읽기·생성·수정. 삭제 정책/참조 정리 미정으로 삭제 차단 |
| ToDo | 같은 그룹 읽기. 그룹장만 생성·수정·삭제. 다른 그룹 반려견 참조 차단 |
| 완료 기록 | 멤버가 자신을 완료자로 생성, 서버 시각 사용. 기존 기록 덮어쓰기 금지. 완료자/그룹장 취소 |
| 변경 제안 | 멤버가 pending으로 작성·조회. 승인/거절은 향후 신뢰 가능한 서버 처리 |
| 요청 | 그룹 멤버끼리 생성·조회. 수신자만 pending → accepted/rejected 변경 |
| 사진 문서 | 멤버 조회만. Storage 연동·삭제 정리 로직 전까지 쓰기 차단 |
| 알림 | 본인 조회·읽음 변경만. 생성/삭제는 클라이언트 차단 |
| 그 외 | 허용하지 않음 |

초대 코드 가입·위임·탈퇴와 제안 승인은 아직 구현하지 않았다. 이 문서의 모델 존재만으로 해당 작업이 가능한 것이 아니다. 서버/DB 담당의 Functions는 Admin SDK 사용 시 보안 규칙을 우회하므로 동일한 인증·그룹 소속·역할·상태 전이 검증을 함수 안에 구현해야 한다. 합의한 사진 삭제 정책(현재 그룹의 업로더 또는 그룹장)은 Storage 구현 시 적용한다.

## 초기 설정과 조회

1. 로그인 후 `users/{uid}`를 서버에서 조회하고 `active_group_id`로 그룹 데이터를 읽는다.
2. 신규 사용자는 보호자 이름·그룹 이름·반려견·초기 할 일을 입력한다.
3. 사용자·그룹·그룹장 멤버십·반려견·할 일을 한 트랜잭션으로 생성한다.
4. 첫 그룹 ID는 UID, 반려견·할 일 ID는 그룹 ID와 입력 항목 ID를 바탕으로 만든다. 재시도하거나 동시에 저장해도 같은 그룹을 재사용하며 저장된 내용을 덮어쓰지 않는다.
5. 사진은 초기 설정 초안과 그룹별 기기 저장소에서 관리한다. 사진 파일 경로를 Firestore에 보내지 않는다.
6. 완료 기록은 사용자가 완료 체크를 했을 때 수행자와 서버 시각을 함께 저장한다.

서버 오류나 그룹 접근 거부를 신규 사용자로 간주하지 않는다. 권한 오류가 발생하면 화면의 그룹 정보를 비우고 재확인을 안내한다. `schema_version`은 저장 형식을 검증하는 내부 메타데이터다.

초기 설정의 문서 수가 커지면 보안 규칙 조회 한도로 인해 트랜잭션이 실패할 수 있다. 현재 화면은 반려견 1마리와 기본 할 일 선택을 제공한다. 대량 등록은 별도 설계한다.

## 앱 연결 범위

- Google 로그인은 Firebase Authentication을 사용한다. 실제 로그인 경로는 GroupApp → GroupViewModel(StateFlow) → GroupRepository다.
- 초기 설정은 실제 1인 그룹과 반려견/초기 ToDo를 생성한다.
- 홈/할 일 탭의 추가·완료는 서버 저장이다. 그룹 공통 ToDo 추가도 가능하다. 수정·삭제 API와 요일별 반복 모델은 있으나 해당 편집 UI는 후속 작업이다.
- 그룹 탭의 반려견 이름·견종·생일·체중 수정은 서버 저장이다. 사용자 프로필 수정 API는 있지만 전용 편집 화면은 후속 작업이다.
- 변경 제안 작성, 요청 전송/응답, 알림 조회/읽음의 Repository 메서드를 제공한다. 관련 UI·승인 Functions·FCM 트리거는 미구현이다.
- 데이터 조회는 서버 기준이며 새로고침/앱 복귀 시 반영된다. 실시간 snapshot listener·페이지 분할·오프라인 쓰기 UX는 아직 구현하지 않았다.
- 샘플 체험은 기기 전용 흐름이다.

Storage·Functions·FCM 서버 연동은 후속 작업이다. 현재 Firebase 프로젝트는 Spark 요금제다.
