// Runs only against the local demo Auth emulator. No production account or real email is used.
import assert from 'node:assert/strict';
import { initializeApp, deleteApp } from 'firebase/app';
import { initializeAuth, inMemoryPersistence, connectAuthEmulator, createUserWithEmailAndPassword,
  sendEmailVerification, checkActionCode, applyActionCode, reload, sendPasswordResetEmail,
  verifyPasswordResetCode, confirmPasswordReset, signOut, signInWithEmailAndPassword } from 'firebase/auth';
import { runAction } from '../src/flow.mjs';
const project = 'demo-dogs-onboarding';
const app = initializeApp({apiKey:'fake-api-key', projectId:project}, `web-${Date.now()}`);
const auth = initializeAuth(app, {persistence:inMemoryPersistence});
connectAuthEmulator(auth, 'http://127.0.0.1:9099', {disableWarnings:true});
const email = `web-${Date.now()}@example.com`;
const client = {checkActionCode: code => checkActionCode(auth,code), applyActionCode: code => applyActionCode(auth,code),
  verifyPasswordResetCode: code => verifyPasswordResetCode(auth,code)};
async function code(type) {
  const response = await fetch(`http://127.0.0.1:9099/emulator/v1/projects/${project}/oobCodes`);
  return (await response.json()).oobCodes.findLast(x => x.email === email && x.requestType === type).oobCode;
}
try {
  const user = (await createUserWithEmailAndPassword(auth,email,'Dogs123!')).user;
  assert.equal(user.emailVerified,false);
  await sendEmailVerification(user);
  const verifyCode = await code('VERIFY_EMAIL');
  assert.equal(await runAction(client,{mode:'verifyEmail',code:verifyCode}),'verified');
  await reload(user); assert.equal(user.emailVerified,true);
  await assert.rejects(runAction(client,{mode:'verifyEmail',code:verifyCode}));
  await assert.rejects(runAction(client,{mode:'verifyEmail',code:'not-a-real-code'}));
  await sendPasswordResetEmail(auth,email);
  const resetCode = await code('PASSWORD_RESET');
  assert.equal(await runAction(client,{mode:'resetPassword',code:resetCode}),'reset');
  await confirmPasswordReset(auth,resetCode,'NewDogs456!');
  await signOut(auth);
  await assert.rejects(signInWithEmailAndPassword(auth,email,'Dogs123!'));
  assert.equal((await signInWithEmailAndPassword(auth,email,'NewDogs456!')).user.uid,user.uid);
  await assert.rejects(verifyPasswordResetCode(auth,resetCode));
  console.log('PASS: Firebase SDK verification, reused/invalid code rejection, password reset, old-password rejection, same-UID login');
} finally { await signOut(auth); await deleteApp(app); }
