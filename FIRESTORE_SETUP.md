# Firestore 설정

DOGS는 팀에서 합의한 **그룹 기반 데이터 모델**을 사용한다. 상세 필드·권한은 [GROUP_SCHEMA.md](GROUP_SCHEMA.md), 역할 분담은 [BACKEND_CONTRACT.md](BACKEND_CONTRACT.md), 앱 실행은 [README.md](README.md)를 참고한다.

## 프로젝트

- Firebase 프로젝트: `dogs-dev-f9a4e`
- Firestore: Standard `(default)` / `asia-northeast3`(서울)
- 인증: Firebase Authentication의 Google 로그인
- 요금제: Blaze
- 파일 저장: 공용 사진첩은 Firebase Storage. 초기 반려견 프로필 사진은 기기 보관

`app/google-services.json`을 준비하고 Google 로그인에 사용할 개발자별 디버그 서명 지문을 Firebase Android 앱에 등록한다. DB 구조를 수정하는 것만으로 설정 파일을 다시 다운로드할 필요는 없다.

## 저장 경로

```text
users/{uid}                              사용자 정보, active_group_id
users/{uid}/notifications/{id}           개인 알림함
groups/{groupId}                         그룹 이름, 그룹장, 시간대
groups/{groupId}/members/{uid}           그룹 소속·역할
groups/{groupId}/dogs/{dogId}            반려견 프로필
groups/{groupId}/todos/{todoId}          할 일
groups/{groupId}/todoLogs/{todoId_day}   날짜별 완료 기록
groups/{groupId}/changeRequests/{id}    변경 제안
groups/{groupId}/requests/{id}          돌봄 요청
groups/{groupId}/photos/{photoId}       사진 메타데이터
```

데이터가 없는 컬렉션은 콘솔에 표시되지 않는다. 사진 문서는 photos Functions가 작성한다. 알림 생성과 제안 승인은 별도 서버 기능으로 연결한다.

## 로그인과 초기 설정

1. 로그인한 사용자의 `users/{uid}`를 서버에서 조회한다.
2. 그룹이 있으면 `active_group_id`로 그룹·멤버·반려견·할 일·오늘 완료 기록을 읽어 홈을 표시한다.
3. 신규 사용자는 이름·그룹 이름·반려견·초기 할 일을 입력한다.
4. 사용자·그룹·그룹장 멤버십·반려견·할 일을 하나의 트랜잭션으로 저장한다. 저장 성공 후 홈으로 이동한다.
5. 재시도할 때 이미 생성된 그룹이 있으면 재사용한다. 서버 조회 실패를 신규 사용자로 처리하지 않는다.

서버 데이터를 기준으로 새로고침과 앱 복귀 시 화면을 갱신한다. 실시간 구독은 아직 없다. 공용 사진첩은 Storage에 저장하며 Firestore에는 서버 경로만 기록한다. 초기 반려견 프로필 사진은 기기 보관이다.

## 보안 규칙

- 그룹 데이터 조회는 현재 그룹 멤버에게만 허용한다.
- ToDo 생성·수정·삭제는 그룹장, 반려견 등록·수정과 완료 체크는 그룹 멤버에게 허용한다.
- 완료 기록에 수행자 UID와 서버 시각을 저장한다. 완료 취소는 완료자 또는 그룹장만 할 수 있다.
- 사용자 프로필과 개인 알림함은 본인만 접근한다.
- 초대·탈퇴·그룹장 위임, 제안 승인 등은 신뢰할 수 있는 서버 처리를 구현하기 전까지 클라이언트에 개방하지 않는다.
- 명시적으로 허용하지 않은 경로는 차단한다.

저장소의 `firestore.rules`를 로컬에서 검증한 뒤 팀에서 검토하여 배포한다. Firebase CLI 로그인이 준비된 환경에서:

```sh
npx firebase-tools deploy --only firestore:rules --project dogs-dev-f9a4e
```

앱 실행이나 GitHub push만으로 규칙이 배포되지는 않는다.

## 빌드와 로컬 테스트

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' npx firebase-tools emulators:start --only auth,firestore --project demo-dogs-onboarding
```

Firebase 에뮬레이터가 실행된 상태에서 별도 터미널로 실행한다.

```sh
node tests/firestore-rules.mjs
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e firestoreEmulator true -e class com.teamdogs.dogs.GroupRepositoryTest com.teamdogs.dogs.test/androidx.test.runner.AndroidJUnitRunner
```

`demo-dogs-onboarding`은 로컬 테스트용 가상 프로젝트 ID다. 통합 테스트는 별도 FirebaseApp을 사용하고 실제 로그인 세션을 변경하지 않는다. 개발 중 데이터와 사진을 유지하려면 앱 삭제나 `pm clear` 대신 `adb install -r`를 사용한다.

## 확인할 동작

- 신규 계정의 그룹 생성과 서버 재조회
- 초기 설정 재시도 시 그룹 중복 생성 및 수정 내용 덮어쓰기 방지
- 반려견 필드, 그룹 공통 ToDo, 요일별 반복 데이터 저장
- 동시 완료 체크, 수행자·시각 보존, 완료 취소
- 그룹 외 사용자 접근 차단과 역할별 권한
- 오프라인 조회 실패 시 오류 표시와 재시도
- 실제 로그인 계정의 홈 복원과 사진의 기기 보관

에뮬레이터에서 첫 Firestore 연결이 지연되면 오류가 표시될 수 있다. 연결 상태를 확인하고 다시 시도한다. 실제 두 번째 기기 동기화, 전체 화면 회귀, FCM 서버 연동은 별도로 확인한다. 사진첩 설정·배포는 PHOTO_ALBUM_DESIGN.md를 참고한다.

## 검증 결과 (2026-11-06)

- Debug / AndroidTest APK 클린 빌드 성공.
- JVM 단위 테스트 9개, 로컬 보안 규칙 검사 34개 통과.
- 그룹 Repository 통합 테스트 5개 통과. 테스트용 로그인 지연으로 첫 실행에서 실패한 동시성 테스트 1개는 재실행하여 통과했다.
- 저장소의 보안 규칙을 실제 Firebase 프로젝트에 게시했다.
- 실제 기기의 최종 홈 조회에서는 서버 응답 시간 초과가 표시됐다. 서버 문서와 자동 테스트 확인과 별개로, 기기 연결 상태를 안정화한 뒤 홈 복원을 재확인해야 한다.


## 이메일 회원가입·인증·비밀번호 재설정

Google 로그인과 이메일/비밀번호 로그인을 함께 사용한다.

### 실제 Firebase 콘솔 설정

1. Authentication → 로그인 방법 → 이메일/비밀번호 → 사용 설정. 기존 Google 공급자는 유지한다. 비밀번호 없는 이메일 링크 로그인은 사용하지 않는다.
2. 가입 정책은 앱에서 공백 없는 8~128자, 숫자·특수문자 포함으로 검사한다. 콘솔의 비밀번호 정책도 팀이 같은 조건으로 맞춘다. 기존 이메일 로그인에는 새 가입 정책을 강제하지 않는다.
3. 템플릿에서 이메일 주소 인증·비밀번호 재설정의 발신자/앱 이름과 실제 표시 문구를 확인한다. 앱은 메일 언어를 `ko`로 요청한다.
4. 아래 보안 규칙을 검토·배포한다. 코드 빌드나 push로 서버 규칙이 자동 변경되지 않는다.

### 앱 동작과 저장

- 로그인 → 회원가입 → 이름·이메일·비밀번호·비밀번호 확인 → 인증 메일 → 앱 복귀 또는 ‘인증 여부 확인’ → 가입 완료 → ‘돌봄 시작하기’ → 초기 설정/홈.
- 계정 생성은 Firebase Authentication, 이름은 Auth 표시 이름에 저장한다. Firestore 사용자·그룹 문서는 기존 초기 설정 트랜잭션에서 생성한다. 비밀번호는 Firestore·기기 저장소·로그에 기록하지 않는다.
- 미인증 이메일 계정은 재시작해도 인증 화면으로 돌아온다. 재조회 시 `reload()`와 `getIdToken(true)`로 서버 규칙에서 쓰는 인증 여부도 갱신한다.
- 이메일/비밀번호 공급자의 미인증 토큰은 Firestore 읽기·쓰기를 차단한다. 기존 Google 및 로컬 익명 테스트 흐름의 권한 정책은 유지한다.
- 인증 메일·재설정 메일 재전송에는 화면에서 60초 대기를 적용한다. Firebase 자체 전송 제한도 적용되며 앱 재시작으로 서버 제한이 해제되지는 않는다.
- 가입 후 이름 저장이나 메일 발송 실패가 발생해도 계정을 재생성하지 않는다. 인증 메일은 재전송하고, 이름은 초기 설정에서 보완할 수 있다.
- 중복 이메일은 기존 로그인 방법/비밀번호 찾기로 안내한다. 앱이 이메일 문자열로 사용자 문서를 합치거나 다른 UID의 그룹 데이터를 옮기지 않는다. 별도의 로그인 수단 연결 UI는 이번 범위에 포함하지 않는다.
- 재설정 안내는 이메일 가입 여부를 노출하지 않는 문구를 사용한다. 메일 링크의 유효기간은 Firebase 설정에 따르며 임의로 ‘30분’이라고 표시하지 않는다.

### 로컬 인증 테스트

위의 Firebase 에뮬레이터 실행 후:

```sh
node tests/email-auth-rules.mjs
adb shell am instrument -w -e authEmulator true -e class com.teamdogs.dogs.EmailAuthRepositoryTest com.teamdogs.dogs.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.teamdogs.dogs.LoginScreenTest,com.teamdogs.dogs.EmailAuthScreenTest com.teamdogs.dogs.test/androidx.test.runner.AndroidJUnitRunner
```

SDK 테스트는 별도 이름의 `demo-dogs-onboarding` FirebaseApp만 사용한다. 인증·재설정 메일 링크는 로컬 에뮬레이터에서 처리하며 실제 메일을 발송하지 않는다.

### 배포 전 확인할 사항

- 이메일/비밀번호 공급자는 2026-11-16 사용자가 콘솔에서 사용 설정했음을 확인했다. 새 보안 규칙 배포는 별도 확인이 필요하다.
- 실제 메일 수신·링크 만료·Google 계정 선택창·두 번째 기기 복원은 테스트 계정으로 확인한다.
- Figma에는 약관 동의 표시가 있으나 확정된 약관/개인정보처리방침 링크가 없어 동작하지 않는 동의 체크박스를 만들지 않았다. 팀 정책 문서 확정 후 실제 링크와 동의 기록을 연결한다.
- Figma MCP 접근 권한과 브라우저 연결이 없어, 앞서 확인한 회원가입·재설정 화면과 기존 Compose 테마를 기준으로 구성했다. 최신 디자인 치수/아이콘의 정확한 일치는 확인하지 못했다.

### 이메일 인증 기능 검증 결과 (2026-11-16)

- Debug 앱과 AndroidTest APK 빌드 성공. JVM 단위 테스트 17개 통과.
- 로컬 기존 보안 규칙 검사 34개와 이메일 인증 여부에 따른 접근 검사 5개 통과.
- Android 화면 테스트 8개와 Firebase SDK 통합 테스트 1개 통과(기기 TestRunner: 9 tests, 0 failed, 0 ignored). 호스트 명령의 대기 제한 이후 기기 로그에서 최종 성공을 확인했다.
- SDK 통합 테스트: 가입, 이름 저장, 인증 메일 링크 처리, 인증 상태/토큰 갱신, 그룹 초기 설정 저장, 재로그인, 비밀번호 재설정, 이전 비밀번호 거부, 새 비밀번호 로그인과 같은 UID·그룹 복원, 중복 가입 거부. 모두 별도 로컬 테스트 프로젝트에서 수행했다.
- 회원가입·이메일 인증·비밀번호 찾기 화면 캡처를 확인했다.
- 실제 프로젝트의 새 규칙 배포와 실제 메일 수신 테스트는 완료하지 않았다. Firebase CLI에 로그인된 계정이 없어 규칙을 배포하지 않았으며, 실제 계정의 메일은 자동 발송하지 않았다.

## 인증 웹페이지

2026-11-16 DOGS 디자인의 이메일 인증·비밀번호 재설정 페이지를 Firebase Hosting에 게시했다. Action URL 변경은 Firebase의 `EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED` 응답으로 적용되지 않아 기존 메일 링크가 유지된다. 배포 주소·테스트·지원 요청 초안은 [auth-web/README.md](auth-web/README.md)를 참고한다.

## 공용 사진첩 서버

사진첩의 함수·Storage 규칙·인덱스는 [PHOTO_ALBUM_DESIGN.md](PHOTO_ALBUM_DESIGN.md)에 정리했다.
`firebase deploy --non-interactive`는 Storage 규칙의 Firestore 서비스 연결 권한 설정을 건너뛸 수 있다.
이 프로젝트에서는 담당자 승인 후 `service-821741695648@gcp-sa-firebasestorage.iam.gserviceaccount.com`에 `roles/firebaserules.firestoreServiceAgent` 역할을 부여했다. 다른 Firebase 프로젝트에 배포할 때는 해당 프로젝트의 서비스 연결 권한도 확인한다.
배포와 실제 앱 검증 상태는 [PROTOTYPE_TESTING.md](PROTOTYPE_TESTING.md)를 따른다.

## 선택 시간과 직접 돌봄 기록

할 일의 due_at 빈 문자열은 시간 미정이다. 이미 한 일은 groups/{groupId}/careRecords/{recordId}에 저장한다.
done_at은 실제 수행 시각, created_at은 서버 작성 시각이다. 그룹 멤버만 본인 이름으로 과거/현재 기록을 만들 수 있고 작성자 또는 그룹장만 삭제할 수 있다.
기존 todoLogs와 careRecords는 화면에서 합쳐서 실제 시각순으로 표시한다. 기존 데이터 이전은 필요 없다. 관련 규칙을 게시하고 실제 앱 저장·조회·삭제를 확인했다.
