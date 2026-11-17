import { createClient } from './client.mjs';
import { APP_LINK, readAction, runAction, passwordError, errorView } from './flow.mjs';
const el = id => document.getElementById(id);
const android = /Android/i.test(navigator.userAgent);
const icons = {
  success: '<svg viewBox="0 0 40 40" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><path d="m10 21 7 7 14-16"/></svg>',
  mail: '<svg viewBox="0 0 40 40" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="5" y="9" width="30" height="23" rx="5"/><path d="m6 11 14 11 14-11"/></svg>',
  error: '<svg viewBox="0 0 40 40" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><circle cx="20" cy="20" r="14"/><path d="M20 12v10m0 5v1"/></svg>',
};
function render({title, detail, step = 'DOGS 계정 확인', icon = 'success', next = '', reset = false, retry = false}) {
  el('card').setAttribute('aria-busy', 'false');
  el('title').textContent = title;
  el('description').textContent = detail;
  el('step').textContent = step;
  document.title = `${title} · DOGS`;
  el('symbol').className = `symbol${icon === 'error' ? ' problem' : ''}`;
  el('symbol').innerHTML = icons[icon]; // Only static icons above, never URL or Firebase response data.
  el('next-copy').textContent = next;
  el('next').hidden = !next;
  el('device-help').hidden = !next;
  el('reset-form').hidden = !reset;
  el('retry').hidden = !retry;
  el('open-app').hidden = !android || reset;
  el('open-app').href = APP_LINK;
}
const views = {
  landing: { title: '함께 돌볼 준비가 됐나요?', detail: 'DOGS에서 받은 메일의 인증 링크를 열어 주세요.', step: 'DOGS에 오신 것을 환영해요', icon: 'mail', next: '앱에서 회원가입을 시작하거나\n인증 메일을 다시 요청할 수 있어요.' },
  verified: { title: '이메일 인증을 마쳤어요', detail: '한 걸음 더 가까워졌어요.\n이제 DOGS에서 함께하는 돌봄을 시작해 보세요.', step: '이메일 인증 완료', next: 'DOGS 앱으로 돌아가 주세요.\n가입 완료 화면에서 ‘돌봄 시작하기’를 누르면 돼요.\n인증 대기 중이라면 ‘인증 여부 확인’을 눌러 주세요.' },
  reset: { title: '새 비밀번호를 설정해요', detail: '앞으로 DOGS에 로그인할 때 사용할\n새 비밀번호를 입력해 주세요.', step: '비밀번호 재설정', icon: 'mail', reset: true },
  passwordSaved: { title: '비밀번호를 변경했어요', detail: '이제 새로운 비밀번호로\nDOGS에 로그인할 수 있어요.', step: '비밀번호 재설정 완료', next: 'DOGS 앱을 열고 이메일과\n새 비밀번호로 로그인해 주세요.' },
  recovered: { title: '이메일 주소를 복구했어요', detail: '계정의 이메일 주소가 이전 주소로 돌아왔어요.', step: '이메일 복구 완료', next: '요청하지 않은 변경이었다면 앱의\n‘비밀번호 찾기’에서 비밀번호도 바꿔 주세요.' },
};
let action, clientPromise;
async function start() {
  el('retry').disabled = true;
  el('card').setAttribute('aria-busy', 'true');
  try {
    if (action.mode === 'landing') { render(views.landing); return; }
    clientPromise ||= createClient().catch(error => { clientPromise = null; throw error; });
    const result = await runAction(await clientPromise, action);
    render(views[result]);
  } catch (error) {
    render({ ...errorView(error), icon: 'error', next: 'DOGS 앱에서 현재 인증 상태를 확인하거나\n새 메일을 요청해 주세요.' });
  } finally { el('retry').disabled = false; }
}
el('retry').addEventListener('click', start);
el('toggle-password').addEventListener('click', () => {
  const visible = el('password').type === 'password';
  el('password').type = visible ? 'text' : 'password';
  el('toggle-password').textContent = visible ? '숨기기' : '보기';
  el('toggle-password').setAttribute('aria-pressed', String(visible));
  el('toggle-password').setAttribute('aria-label', visible ? '새 비밀번호 숨기기' : '새 비밀번호 표시');
});
el('reset-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (el('reset-submit').disabled) return;
  const password = el('password').value;
  const invalid = passwordError(password, el('confirmation').value);
  el('form-error').textContent = invalid || '';
  if (invalid) return;
  el('reset-submit').disabled = true;
  el('reset-submit').textContent = '비밀번호를 저장하고 있어요…';
  try {
    await (await clientPromise).confirmPasswordReset(action.code, password);
    el('reset-form').reset();
    render(views.passwordSaved);
  } catch (error) {
    if (error.code === 'auth/weak-password' || error.code === 'auth/password-does-not-meet-requirements') {
      el('form-error').textContent = '보안 조건에 맞지 않는 비밀번호예요. 더 강한 비밀번호를 입력해 주세요.';
    } else if (['auth/expired-action-code', 'auth/invalid-action-code', 'auth/user-disabled', 'auth/user-not-found'].includes(error.code)) {
      el('reset-form').reset();
      render({ ...errorView(error), icon: 'error', next: '앱의 ‘비밀번호 찾기’에서 새 메일을 받아 주세요.' });
    } else {
      el('form-error').textContent = '저장 결과를 확인하지 못했어요. 다시 시도하거나 앱에서 새 비밀번호로 로그인을 확인해 주세요.';
    }
  } finally {
    el('reset-submit').disabled = false;
    el('reset-submit').textContent = '새 비밀번호 저장하기';
  }
});
// Preview branches are removed from the production bundle by esbuild.
const preview = __LOCAL_PREVIEW__ && ['localhost', '127.0.0.1'].includes(location.hostname)
  ? new URLSearchParams(location.search).get('preview') : null;
if (preview) {
  render(views[preview] || { ...errorView({code: `auth/${preview}`}), icon: 'error', next: '앱에서 인증 메일을 다시 받아 주세요.' });
  el('reset-form').querySelector('button[type="submit"]').disabled = true;
} else {
  try {
    action = readAction(location.search);
    // Keep the one-time code only in this page's memory, not in history or browser storage.
    history.replaceState(null, '', location.pathname);
    await start();
  } catch (error) { render({ ...errorView(error), icon: 'error', next: '메일에서 받은 링크를 다시 확인해 주세요.' }); }
}
