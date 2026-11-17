// Run only against local demo Firebase emulators. No real emails or project credentials.
import assert from 'node:assert/strict';
const project = 'demo-dogs-onboarding';
const auth = 'http://127.0.0.1:9099';
const firestore = `http://127.0.0.1:8080/v1/projects/${project}/databases/(default)/documents`;
async function api(path, body) {
  const response = await fetch(auth + path, {method: body ? 'POST' : 'GET',
    headers: {'Content-Type': 'application/json'}, body: body ? JSON.stringify(body) : undefined});
  const data = await response.json(); assert.equal(response.status, 200, JSON.stringify(data)); return data;
}
const endpoint = action => `/identitytoolkit.googleapis.com/v1/accounts:${action}?key=fake`;
const email = `rules-${Date.now()}@example.com`;
const account = await api(endpoint('signUp'), {email, password: 'Dogs123!', returnSecureToken: true});
async function read(token) {
  return (await fetch(`${firestore}/users/${account.localId}`, {headers: {Authorization: `Bearer ${token}`}})).status;
}
async function write(token) {
  return (await fetch(`${firestore}/users/${account.localId}`, {method: 'PATCH',
    headers: {'Content-Type': 'application/json', Authorization: `Bearer ${token}`},
    body: JSON.stringify({fields: {id: {stringValue: account.localId}, email: {stringValue: email},
      name: {stringValue: '보호자'}, profile_img: {stringValue: ''}, active_group_id: {nullValue: null}, schema_version: {integerValue: '2'}}})})).status;
}
assert.equal(await read(account.idToken), 403, 'Unverified email must not read app data');
assert.equal(await write(account.idToken), 403, 'Unverified email must not write profile');
console.log('PASS: unverified email read/write blocked');
await api(endpoint('sendOobCode'), {requestType: 'VERIFY_EMAIL', idToken: account.idToken});
const codes = await api(`/emulator/v1/projects/${project}/oobCodes`);
const code = codes.oobCodes.find(c => c.email === email && c.requestType === 'VERIFY_EMAIL').oobCode;
await api(endpoint('update'), {oobCode: code});
const verified = await api(endpoint('signInWithPassword'), {email, password: 'Dogs123!', returnSecureToken: true});
assert.equal(await write(verified.idToken), 200, 'Verified account can write own valid profile');
assert.equal(await read(verified.idToken), 200, 'Verified account can read own profile');
assert.equal(await read(account.idToken), 403, 'Stale unverified token remains denied');
console.log('PASS: verified token allowed; stale token still blocked');
