# 그룹 공용 사진첩

2026-11-16. 가족 구성원이 찍은 반려견 사진을 그룹 안에서 공유한다.

## 사용 흐름

사진첩 → + 사진 → 시스템 사진 선택기 → 미리보기·반려견 선택 → 업로드.
태그는 한 개만 선택할 수 있고 기본값은 **공통**이다. 선택하지 않으면 `dog_id=null`이다.

| 사진 태그 | 전체 | 공통 | 해당 반려견 |
|---|---|---|---|
| 선택 안 함 | 표시 | 표시 | 표시하지 않음 |
| 콩이 | 표시 | 표시하지 않음 | 콩이 필터에 표시 |

사진을 여러 폴더에 복제하지 않고 Firestore 조건으로 걸러서 보여 준다. 사진 목록에는 사진만 표시하고 하단 태그 글자는 생략한다. 반려견별 구분은 상단 필터로 선택한다. 업로드 전 미리보기는 유지하며 사진 아래 여백과 둥근 단일 선택 버튼으로 태그를 고른다.
최신순 30개씩 조회하며 ‘사진 더 보기’로 다음 페이지를 가져온다. 사진첩 진입·앱 복귀·새로고침 시 갱신한다. 실시간 구독은 이번 범위에 포함하지 않는다.
상세 보기에서는 사진 전체 비율을 유지하고, 삭제 권한이 있으면 삭제 버튼과 확인창을 보여 준다.

## 파일과 문서

```text
Firestore: groups/{groupId}/photos/{photoId}
Storage:   groups/{groupId}/photos/{photoId}/upload.jpg  (임시 업로드)
           groups/{groupId}/photos/{photoId}/image.jpg   (서버가 변환한 본문)
           groups/{groupId}/photos/{photoId}/thumb.jpg   (목록용)
```

문서 필드: `id`, `group_id`, `dog_id`, `uploader_id`, `storage_path`, `thumbnail_path`, `status`, `created_at`, `updated_at`, `cleanup_at`(정리 대기일 때만), `size_bytes`(본문 확정 이후).
기존 `url` 필드는 빈 문자열로 유지하며 화면에서 사용하지 않는다. 이미지 다운로드는 인증된 Storage SDK로 수행한다. 파일을 가져온 후 Coil로 표시한다.

- ID는 사진마다 UUID를 생성한다. 실패 후 재시도는 동일 ID를 사용한다.
- 기기에서 EXIF 방향을 보정하고 기존 이미지 가져오기 코드로 긴 변 약 1,600px 이하 JPEG를 만든다. 5MiB 초과 파일은 업로드하지 않는다.
- 서버에서 실제 JPEG인지 검사하고 최대 2,048px/품질 80으로 재인코딩한다. 원본 EXIF/GPS는 복사하지 않는다. 썸네일은 최대 400px/품질 75다. 최종 본문 5MiB, 썸네일 500KiB 제한을 검사한다.
- 사용자가 사진을 자를 필요는 없다. 목록 표시만 정사각형으로 자르고 상세는 전체 비율로 보여 준다. 원본 보관용 서비스는 아니다.
- 초기 설정에서 고른 반려견 프로필 사진과 과거 기기 사진은 자동으로 업로드하지 않는다. 프로필 사진 서버 연결은 별도 기능이다.

## 서버 함수와 상태

| 함수 | 역할 |
|---|---|
| preparePhotoUpload(groupId, photoId, dogId?) | 인증·멤버십·반려견 검증, uploading 문서 예약, 임시 파일 존재 여부 반환 |
| completePhotoUpload(groupId, photoId) | 실제 파일 검사, 본문·썸네일 생성, ready 확정 |
| deletePhoto(groupId, photoId) | 현재 멤버 + 업로더/현재 그룹장 검증, 파일 삭제 |
| cleanLatePhotoFile | 삭제 이후 늦게 도착한 파일을 세대 번호 기준으로 정리 |
| cleanAbandonedPhotos | 하루마다 만료된 업로드·실패한 삭제·불필요한 임시 원본 정리 |

상태: `uploading → ready → deleting → deleted`. 업로드 실패는 uploading 상태를 유지하여 재시도하며, 만료되면 정리한다.
ready만 공용 목록에 표시한다. 삭제된 문서는 목록에서 사라지며, 같은 ID 재사용과 지연 업로드를 막기 위한 작은 deleted 기록은 남긴다. 이미지 파일은 삭제한다.
Firestore와 Storage는 하나의 트랜잭션으로 묶이지 않으므로 삭제 상태를 먼저 기록하고 파일 삭제를 재시도한다. 정리 작업은 만료 시각이 있는 문서만 한 번에 최대 200개 처리한다. 정상 사진 전체를 매일 다시 읽지 않는다.

기기에는 업로드 작업 ID·태그·변환 파일을 백업 제외 폴더에 보관한다. 앱 종료 후 같은 계정·그룹으로 돌아오면 재시도/취소할 수 있다. 백그라운드 업로드 지속은 보장하지 않는다. 완료 응답 유실 시 동일 ID의 ready 상태를 확인하여 중복 생성을 막는다.

## 권한

- 현재 그룹 멤버: ready 사진 목록·파일 조회, 자신의 새 사진 업로드.
- 현재 그룹 멤버이면서 업로더 본인 또는 현재 그룹장: 삭제.
- 그룹 밖 사용자·탈퇴자·미인증 이메일 가입자: 접근 거부.
- 앱의 사진 문서 직접 쓰기와 Storage 직접 삭제는 차단한다. 서버 함수가 권한을 검증하고 처리한다.
- Storage 업로드는 예약된 경로에 임시 파일 하나를 새로 만드는 것만 허용한다. 기존 파일 덮어쓰기와 본문·썸네일 직접 업로드는 차단한다.
- 공개 다운로드 토큰 URL을 저장하지 않는다. 완성 이미지에는 서버가 다운로드 토큰을 붙이지 않으며, 앱은 다운로드/캐시 사용 전에 Storage 접근 권한을 확인한다.
- 캐시는 사용자·그룹·화면 세션별로 분리하며 세션 종료 시 제거한다. 이미 내려받거나 캡처한 사진 자체를 원격 회수하는 기능은 아니다.

## 앱 연결과 협업

`FirebasePhotoRepository → PhotoViewModel → PhotoAlbum` 구조다.
Repository는 목록·페이지 조회, 사진 준비·업로드, 삭제 호출, 인증된 다운로드를 담당한다.
ViewModel은 필터·목록·진행률·실패/재시도 상태를 관리한다.
Compose 화면은 이 상태와 이벤트를 사용한다. 기존 홈의 최근 사진도 같은 Repository로 서버 데이터를 읽는다.

서버 코드는 `functions/`의 `photos` 코드베이스로 분리했다. 기존 알림·변경 제안 서버 기능을 대신 구현한 것은 아니다. 서버/DB 담당은 이 코드와 규칙을 함께 리뷰하고 이후 유지보수 범위를 조율한다.

## 설정과 배포

- Firebase 프로젝트: dogs-dev-f9a4e (Blaze)
- 버킷: dogs-dev-f9a4e.firebasestorage.app / 서울 asia-northeast3
- Functions: asia-northeast3, Node.js 22, 최대 인스턴스 2 (이미지 변환 함수는 인스턴스당 동시 처리 1개)
- 앱은 버킷 주소를 명시하므로 기존 google-services.json을 버킷 추가 때문에 다시 받을 필요는 없다.
- 일반 팀원 앱 실행에는 함수 재배포가 필요 없다. 이미 배포된 서버를 사용한다.

```bash
npm ci --prefix functions
firebase deploy --project dogs-dev-f9a4e --only functions:photos,storage,firestore
```

Storage 규칙의 Firestore 조회를 위한 서비스 연결 권한이 필요하다. 배포 도구가 안내하면 해당 프로젝트의 공식 연결 권한을 설정한다. 프로젝트 담당자 승인 후 서비스 연결 역할을 설정했다. 함수 5개 ACTIVE·사진 조회 인덱스 READY를 확인했고, 실제 Android 업로드·재조회·다운로드·삭제 테스트도 통과했다. 상세 검증 결과는 PROTOTYPE_TESTING.md에 기록한다.

## 검증

```bash
npm test --prefix functions
PHOTO_BUCKET=demo-dogs-photos.firebasestorage.app firebase emulators:exec --project demo-dogs-photos --only auth,firestore,storage,functions 'node functions/test/integration.cjs'
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.teamdogs.dogs.PhotoScreenTest
```

현재 Gradle의 `connectedDebugAndroidTest` 실행은 종료 후 앱을 제거하므로 전용 테스트 에뮬레이터에서만 사용한다. 실제 계정과 기기 데이터를 유지해야 하는 테스트는 아래 직접 실행 방식을 사용한다.

실제 서버 테스트는 로그인·초기 설정된 개발 기기에 한해 `PhotoRepositoryTest`를 `productionPhotoSmoke=true`로 명시하여 실행한다. 작은 합성 사진을 생성해 재조회·다운로드·삭제하고, 사용자 사진은 사용하지 않는다. 기본 테스트 실행에서는 건너뛴다.

추가 범위: 다중 태그, 태그 변경 UI, 여러 장 업로드, 프로필 사진 서버 저장, 실시간 목록, 다른 실제 기기에서의 수동 확인.

## 공식 참고

- [Storage 권한 조건과 Firestore 멤버십 조회](https://firebase.google.com/docs/storage/security/rules-conditions)
- [Android Storage 다운로드](https://firebase.google.com/docs/storage/android/download-files)
- [Firebase Callable Functions](https://firebase.google.com/docs/functions/callable)

### 실제 로그인 기기의 데이터를 유지하는 테스트

```bash
./gradlew assembleDebug assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# 앱에서 로그인하고 홈 진입 후 실행한다. 테스트 종료 시 앱을 제거하지 않는다.
adb shell am instrument -w -e class com.teamdogs.dogs.PhotoRepositoryTest -e productionPhotoSmoke true com.teamdogs.dogs.test/androidx.test.runner.AndroidJUnitRunner
```

`adb`는 Android SDK의 platform-tools에 있다. 기존 앱 데이터가 있는 기기에서 `uninstall` 또는 `pm clear`를 실행하지 않는다.
