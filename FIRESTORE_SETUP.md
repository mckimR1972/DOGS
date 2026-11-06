# Firestore 설정

DOGS는 팀에서 합의한 **그룹 기반 데이터 모델**을 사용한다. 상세 필드·권한은 [GROUP_SCHEMA.md](GROUP_SCHEMA.md), 역할 분담은 [BACKEND_CONTRACT.md](BACKEND_CONTRACT.md), 앱 실행은 [README.md](README.md)를 참고한다.

## 프로젝트

- Firebase 프로젝트: `dogs-dev-f9a4e`
- Firestore: Standard `(default)` / `asia-northeast3`(서울)
- 인증: Firebase Authentication의 Google 로그인
- 요금제: Spark
- 파일 저장: 현재 기기 보관. Firebase Storage 업로드는 후속 작업

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

데이터가 없는 컬렉션은 콘솔에 표시되지 않는다. 사진 문서 쓰기, 알림 생성과 제안 승인 등은 관련 서버 기능을 구현할 때 연결한다.

## 로그인과 초기 설정

1. 로그인한 사용자의 `users/{uid}`를 서버에서 조회한다.
2. 그룹이 있으면 `active_group_id`로 그룹·멤버·반려견·할 일·오늘 완료 기록을 읽어 홈을 표시한다.
3. 신규 사용자는 이름·그룹 이름·반려견·초기 할 일을 입력한다.
4. 사용자·그룹·그룹장 멤버십·반려견·할 일을 하나의 트랜잭션으로 저장한다. 저장 성공 후 홈으로 이동한다.
5. 재시도할 때 이미 생성된 그룹이 있으면 재사용한다. 서버 조회 실패를 신규 사용자로 처리하지 않는다.

서버 데이터를 기준으로 새로고침과 앱 복귀 시 화면을 갱신한다. 실시간 구독은 아직 없다. 사진 파일과 경로는 기기에 보관하며 Firestore 문서에 기기 절대 경로를 저장하지 않는다.

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

에뮬레이터에서 첫 Firestore 연결이 지연되면 오류가 표시될 수 있다. 연결 상태를 확인하고 다시 시도한다. 실제 두 번째 기기 동기화, 전체 화면 회귀, Storage·Functions·FCM 서버 연동은 별도로 확인한다.

## 검증 결과 (2026-11-06)

- Debug / AndroidTest APK 클린 빌드 성공.
- JVM 단위 테스트 9개, 로컬 보안 규칙 검사 34개 통과.
- 그룹 Repository 통합 테스트 5개 통과. 테스트용 로그인 지연으로 첫 실행에서 실패한 동시성 테스트 1개는 재실행하여 통과했다.
- 저장소의 보안 규칙을 실제 Firebase 프로젝트에 게시했다.
- 실제 기기의 최종 홈 조회에서는 서버 응답 시간 초과가 표시됐다. 서버 문서와 자동 테스트 확인과 별개로, 기기 연결 상태를 안정화한 뒤 홈 복원을 재확인해야 한다.
