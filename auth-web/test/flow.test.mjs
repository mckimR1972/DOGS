import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readAction, runAction, passwordError, errorView } from '../src/flow.mjs';
test('empty link is informational, not success', () => assert.deepEqual(readAction(''), {mode: 'landing'}));
test('malformed and unsupported links cannot run', () => {
  for (const s of ['?mode=verifyEmail', '?oobCode=x', '?mode=signIn&oobCode=x']) assert.throws(() => readAction(s));
});
test('untrusted redirect and API key are never used', () => assert.deepEqual(
  readAction('?mode=verifyEmail&oobCode=x&continueUrl=https://evil.example&apiKey=other'), {mode: 'verifyEmail', code: 'x'}));
test('success requires completed server operation', async () => {
  let applied = false;
  const client = {checkActionCode: async () => ({operation:'VERIFY_EMAIL'}), applyActionCode: async () => { applied = true; }};
  assert.equal(await runAction(client, {mode:'verifyEmail',code:'x'}), 'verified'); assert.ok(applied);
});
test('server error cannot show success', async () => {
  await assert.rejects(runAction({checkActionCode: async () => ({operation:'VERIFY_EMAIL'}),
    applyActionCode: async () => { throw {code:'auth/expired-action-code'}; }}, {mode:'verifyEmail',code:'x'}));
});
test('code operation must match requested operation', async () => {
  let applied = false;
  await assert.rejects(runAction({checkActionCode: async () => ({operation:'RECOVER_EMAIL'}), applyActionCode: async () => {applied = true;}}, {mode:'verifyEmail',code:'x'}));
  assert.equal(applied, false);
});
test('reset checks code without changing password on page load', async () => {
  let checked = false;
  assert.equal(await runAction({verifyPasswordResetCode: async () => {checked = true;}}, {mode:'resetPassword',code:'x'}), 'reset');
  assert.ok(checked);
});
test('recovery keeps existing action links functional', async () => {
  assert.equal(await runAction({checkActionCode: async () => ({operation:'RECOVER_EMAIL'}), applyActionCode: async () => {}}, {mode:'recoverEmail',code:'x'}), 'recovered');
});
test('reset password validation matches app policy', () => {
  assert.equal(passwordError('Dogs123!', 'Dogs123!'), null);
  for (const p of ['Dogs1234', 'Dogs dogs1!', 'Dogs!!!!', 'a1!', 'x'.repeat(129)+'1!']) assert.ok(passwordError(p,p));
  assert.equal(passwordError('Dogs123!', 'Wrong123!'), '두 비밀번호가 서로 달라요.');
});
test('expired, used and network links have honest recoverable copy', () => {
  assert.equal(errorView({code:'auth/expired-action-code'}).retry, false);
  assert.match(errorView({code:'auth/invalid-action-code'}).detail, /이미 사용/);
  assert.equal(errorView({code:'auth/network-request-failed'}).retry, true);
});
