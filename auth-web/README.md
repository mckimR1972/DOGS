# DOGS 계정 인증 페이지

DOGS 앱과 같은 초록색·밝은 배경으로 구성한 반응형 Firebase 이메일 액션 페이지다.

## 현재 상태 (2026-11-16)

- 게시 완료: https://dogs-dev-f9a4e-auth.web.app/auth/action
- 프로젝트: `dogs-dev-f9a4e`, 별도 Hosting 사이트: `dogs-dev-f9a4e-auth`.
- **메일 템플릿 연결은 미완료.** `notification.sendEmail.callbackUri` 변경 요청이 HTTP 400 `EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED`로 거부됐다. 기존 Firebase 기본 링크는 그대로 유지된다.
- 페이지를 직접 열면 안내 화면만 나온다. 실제 인증 완료 화면은 유효한 메일 코드가 Firebase에서 검증된 경우에만 표시한다.
- Firebase 지원팀에서 제한을 확인하고 아래 Action URL을 적용한 뒤, 새로 발송한 메일로 최종 연결을 확인해야 한다.

## 지원하는 흐름

- `verifyEmail`: `checkActionCode`로 작업 종류를 확인하고 `applyActionCode` 완료 후 인증 성공 표시.
- `resetPassword`: 코드 검증 후 비밀번호 입력. 앱과 같은 비밀번호 조건을 확인하고 `confirmPasswordReset` 실행.
- `recoverEmail`: 작업 종류 확인 후 이메일 복구, 비밀번호 변경 안내. 별도 메일을 자동 발송하지 않는다.
- 만료·재사용·잘못된 코드·네트워크 오류 안내.
- Android에서는 `DOGS 앱 열기` 버튼으로 `dogs://auth/complete`를 연다. 앱은 링크만으로 인증 처리하지 않고 Firebase 인증 상태를 다시 읽는다. 기존 APK에는 이 링크 설정이 없으므로 새 빌드를 설치한다.
- 컴퓨터·다른 기기에서는 DOGS를 실행한 기기로 돌아가는 방법을 안내한다.

## 배포 및 테스트

저장소 루트에서 실행한다. Node.js 22 이상 권장.

```sh
npm ci --prefix auth-web
npm test --prefix auth-web
npm run build --prefix auth-web
npx firebase-tools deploy --only hosting --project dogs-dev-f9a4e
```

`firebase.json`의 Hosting 설정은 인증 전용 사이트만 대상으로 한다. Firestore 규칙은 이 명령으로 배포되지 않는다. 기존 기본 Hosting 사이트도 수정하지 않는다.

실제 SDK 로컬 테스트:

```sh
npx firebase-tools emulators:start --only auth --project demo-dogs-onboarding
# 별도 터미널
node auth-web/test/firebase.integration.mjs
```

디자인 미리보기는 `auth-web` 폴더에서 `node build.mjs --preview` 후 `preview-dist`를 localhost 서버로 연다. `?preview=verified`, `?preview=reset`, `?preview=expired-action-code`로 확인한다. 미리보기 코드는 운영 빌드에서 제거되며 실제 인증으로 사용되지 않는다.

## 보안·설정

- Firebase SDK를 로컬 번들로 게시하며 외부 폰트·분석 스크립트를 사용하지 않는다.
- Firebase Hosting의 `/__/firebase/init.json`에서 공개 웹 설정을 받아 프로젝트 ID가 `dogs-dev-f9a4e`인지 확인한다. Android 구성 파일이나 관리자 키를 웹으로 복사하지 않는다.
- URL의 `apiKey`, `continueUrl`을 신뢰하지 않는다. 외부 주소로 자동 이동하지 않는다.
- 인증 코드를 읽은 뒤 주소창에서 제거한다. 비밀번호·코드를 로그, 브라우저 저장소, Firestore에 저장하지 않는다.
- `Referrer-Policy: no-referrer`, CSP, iframe 차단, no-store 헤더를 적용한다.
- 설정은 페이지 게시와 별개다. 메일 연결을 바꾸기 전 기존 주소를 기록하고, 문제가 생기면 기존 주소로 복원한다.

## Firebase 지원 요청 초안

https://firebase.google.com/support 에서 Authentication 관련 문의에 아래 내용을 사용할 수 있다. 아직 전송하지 않았다.

```text
Subject: EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED when setting a custom email action URL

Project ID: dogs-dev-f9a4e
Project number: 821741695648
Plan: Blaze

We built and deployed a branded account action handler for our Android app DOGS
on a Firebase Hosting site in the same project. It uses the official Firebase
Web SDK and handles verifyEmail, resetPassword, and recoverEmail.

Requested Action URL:
https://dogs-dev-f9a4e-auth.web.app/auth/action

Current Action URL:
https://dogs-dev-f9a4e.firebaseapp.com/__/auth/action

Updating notification.sendEmail.callbackUri through projects.updateConfig
returns HTTP 400 EMAIL_TEMPLATE_UPDATE_NOT_ALLOWED. The endpoint is deployed
and returns HTTP 200; SDK communication with Firebase Auth was verified.

Please review the restriction and advise how we can enable this change,
or apply the requested Action URL if permitted. We want to keep Firebase's
existing email delivery and authentication providers unchanged.
```

## 검증 결과

- 액션 처리·비밀번호 정책·오류 처리 단위 테스트 10개 통과.
- 로컬 Firebase SDK: 인증 성공, 재사용/잘못된 코드 거부, 비밀번호 재설정, 이전 비밀번호 거부, 동일 UID 로그인 통과.
- 실제 Hosting HTML·JS·Firebase 구성 응답 HTTP 200 확인.
- 실제 브라우저에서 운영 페이지의 잘못된 인증 코드가 오류 화면으로 표시되는 것 확인.
- 성공·만료·비밀번호 입력 미리보기 화면 확인.
- Android 빌드와 기존 단위 테스트 통과. 수정 APK 설치 및 앱 복귀 URI 라우팅 확인.
- 새 메일에서 커스텀 페이지가 열리는 종단 간 확인은 템플릿 제한 해제 후 진행해야 한다.

참고: https://firebase.google.com/docs/auth/custom-email-handler
