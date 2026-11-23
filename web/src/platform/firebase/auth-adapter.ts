import { GoogleAuthProvider, linkWithPopup, onAuthStateChanged, signInAnonymously, type Auth } from 'firebase/auth';
import { err, ok, type Result } from '@/shared/result';

/** The signed-in trader, without SDK types. */
export interface TraderIdentity {
  readonly uid: string;
  readonly anonymous: boolean;
  readonly email: string | null;
}

export type AuthError = 'NEEDS_NETWORK' | 'POPUP_CLOSED' | 'CREDENTIAL_IN_USE' | 'UNKNOWN';

export interface AuthAdapter {
  /** Resolves with the persisted user, or signs in anonymously (needs network once). */
  ensureSignedIn(): Promise<Result<TraderIdentity, AuthError>>;
  /** Upgrades the anonymous account to Google, keeping the same uid and data. */
  linkGoogle(): Promise<Result<TraderIdentity, AuthError>>;
  current(): TraderIdentity | null;
  deleteCurrentUser(): Promise<void>;
}

function identityOf(auth: Auth): TraderIdentity | null {
  const user = auth.currentUser;
  return user === null ? null : { uid: user.uid, anonymous: user.isAnonymous, email: user.email };
}

function codeOf(error: unknown): string {
  return typeof error === 'object' && error !== null && 'code' in error ? String(error.code) : '';
}

function waitForRestore(auth: Auth): Promise<void> {
  return new Promise((resolve) => {
    const stop = onAuthStateChanged(auth, () => {
      stop();
      resolve();
    });
  });
}

/** Wraps Firebase Auth: anonymous first, Google upgrade with `linkWithPopup`. */
export function createAuthAdapter(auth: Auth): AuthAdapter {
  return {
    async ensureSignedIn() {
      await waitForRestore(auth);
      const existing = identityOf(auth);
      if (existing !== null) return ok(existing);
      try {
        await signInAnonymously(auth);
        const identity = identityOf(auth);
        return identity === null ? err('UNKNOWN') : ok(identity);
      } catch (error) {
        return err(codeOf(error) === 'auth/network-request-failed' ? 'NEEDS_NETWORK' : 'UNKNOWN');
      }
    },
    async linkGoogle() {
      const user = auth.currentUser;
      if (user === null) return err('UNKNOWN');
      try {
        await linkWithPopup(user, new GoogleAuthProvider());
        const identity = identityOf(auth);
        return identity === null ? err('UNKNOWN') : ok(identity);
      } catch (error) {
        const code = codeOf(error);
        if (code === 'auth/popup-closed-by-user') return err('POPUP_CLOSED');
        if (code === 'auth/credential-already-in-use') return err('CREDENTIAL_IN_USE');
        return err(code === 'auth/network-request-failed' ? 'NEEDS_NETWORK' : 'UNKNOWN');
      }
    },
    current: () => identityOf(auth),
    async deleteCurrentUser() {
      await auth.currentUser?.delete();
    },
  };
}
