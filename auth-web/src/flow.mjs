export const PROJECT_ID = 'dogs-dev-f9a4e';
export const APP_LINK = 'intent://auth/complete#Intent;scheme=dogs;package=com.teamdogs.dogs;end';
export function readAction(search) {
  const p = new URLSearchParams(search);
  const mode = p.get('mode'), code = p.get('oobCode');
  if (!mode && !code) return { mode: 'landing' };
  if (!['verifyEmail', 'resetPassword', 'recoverEmail'].includes(mode) || !code || code.length > 2048)
    throw Object.assign(new Error('Invalid action'), { code: 'auth/invalid-action-code' });
  // Never follow continueUrl or initialize a different project using an untrusted query parameter.
  return { mode, code };
}
export function passwordError(password, confirmation) {
  if (password.length < 8 || password.length > 128 || !/[0-9]/u.test(password) ||
      !/[^\p{L}\p{N}\s]/u.test(password) || /\s/u.test(password))
    return '비밀번호는 공백 없이 8~128자, 숫자와 특수문자를 포함해 주세요.';
  if (password !== confirmation) return '두 비밀번호가 서로 달라요.';
  return null;
}
export async function runAction(client, action) {
  if (action.mode === 'resetPassword') {
    await client.verifyPasswordResetCode(action.code);
    return 'reset';
  }
  const info = await client.checkActionCode(action.code);
  const expected = action.mode === 'verifyEmail' ? 'VERIFY_EMAIL' : 'RECOVER_EMAIL';
  if (info.operation !== expected)
    throw Object.assign(new Error('Wrong operation'), { code: 'auth/invalid-action-code' });
  await client.applyActionCode(action.code);
  return action.mode === 'verifyEmail' ? 'verified' : 'recovered';
}
export function errorView(error) {
  switch (error?.code) {
    case 'auth/expired-action-code':
      return { title: '링크가 만료됐어요', detail: '앱에서 인증 메일 또는 비밀번호 재설정 메일을 다시 받아 주세요.', retry: false };
    case 'auth/invalid-action-code':
      return { title: '링크를 확인해 주세요', detail: '이미 사용했거나 올바르지 않은 링크예요. 인증 링크를 이미 눌렀다면 앱에서 인증 여부를 확인해 주세요.', retry: false };
    case 'auth/user-disabled': case 'auth/user-not-found':
      return { title: '요청을 완료할 수 없어요', detail: '계정 상태를 확인할 수 없어요. 앱에서 다시 로그인해 주세요.', retry: false };
    default:
      return { title: '잠시 연결이 끊겼어요', detail: '인터넷 연결을 확인하고 다시 시도해 주세요. 계속 문제가 생기면 잠시 후 메일의 링크를 다시 열어 주세요.', retry: true };
  }
}
