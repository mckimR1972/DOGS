import { initializeApp } from 'firebase/app';
import { initializeAuth, inMemoryPersistence, checkActionCode, applyActionCode,
  verifyPasswordResetCode, confirmPasswordReset } from 'firebase/auth';
import { PROJECT_ID } from './flow.mjs';
export async function createClient() {
  // Hosting supplies this project's public web configuration; no admin credential is shipped.
  const response = await fetch('/__/firebase/init.json', { cache: 'no-store', credentials: 'omit' });
  if (!response.ok) throw new Error('Configuration unavailable');
  const config = await response.json();
  if (config.projectId !== PROJECT_ID || !config.apiKey) throw new Error('Unexpected Firebase project');
  const auth = initializeAuth(initializeApp(config), { persistence: inMemoryPersistence });
  auth.languageCode = 'ko';
  return {
    checkActionCode: code => checkActionCode(auth, code),
    applyActionCode: code => applyActionCode(auth, code),
    verifyPasswordResetCode: code => verifyPasswordResetCode(auth, code),
    confirmPasswordReset: (code, password) => confirmPasswordReset(auth, code, password),
  };
}
