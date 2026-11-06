# DOGS 백엔드 협업 기준

팀에서 논의한 그룹 중심 설계에 따라 구현한다. [팀 목표](TEAM_SPEC.md)와 [현재 데이터 계약](GROUP_SCHEMA.md)을 기준으로 협업한다.

- Firebase 프로젝트: `dogs-dev-f9a4e` / Android: `com.teamdogs.dogs` / Firestore: `(default)`, `asia-northeast3`.
- Firebase Authentication의 UID를 사용자 식별자로 사용한다.
- 사용자·그룹·멤버·반려견·할 일·완료 기록을 각각 문서로 저장한다. 반려견과 돌봄 데이터는 그룹에 속한다.
- 사용자 프로필은 본인만 읽는다. 그룹 데이터는 현재 멤버만 읽고, ToDo 생성·수정·삭제는 그룹장만 가능하다.
- 정확한 경로·필드·권한·구현 범위는 [GROUP_SCHEMA.md](GROUP_SCHEMA.md)에 정의한다.
- 사진 저장은 Firebase Storage를 사용할 계획이다. 업로드는 아직 연결하지 않았으며 현재 사진은 기기에 보관한다. Supabase는 도입하지 않는다.
- 사진 조회·업로드는 그룹 멤버, 삭제는 해당 그룹의 업로더 또는 그룹장에게 허용하는 정책으로 구현한다.
- 현재 Spark 요금제다. Storage·Functions 도입 시 요금제와 비용을 팀에서 결정한다.
- 앱 로직 담당: 인증·사진·FCM 수신·알림함·Repository·ViewModel.
- 서버/DB 담당: Firestore 스키마·보안 규칙·Functions 승인/알림 처리.
- 프론트엔드 담당: Compose 화면·Navigation.
- 스키마와 권한을 변경할 때 코드·테스트·문서를 함께 검토한다. 모델만 선언된 기능과 화면·서버까지 연결된 기능을 구분한다.
- `main` 직접 push 금지. `dev`에서 기능 브랜치 생성 → `dev` 대상 PR → 팀원 1명 이상 리뷰 후 merge.
- 같은 Firebase DB는 Git 브랜치별로 분리되지 않는다. 코드 push만으로 규칙이나 Functions가 배포되지 않는다.

실행·검증 절차는 [FIRESTORE_SETUP.md](FIRESTORE_SETUP.md)를 참고한다.
