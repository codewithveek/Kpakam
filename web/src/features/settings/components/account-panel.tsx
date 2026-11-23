import { useState } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import { MESSAGES } from '@/shared/messages/en';
import { Button } from '@/shared/ui/button';
import { Card } from '@/shared/ui/card';
import { buildExport, deleteAccount, downloadBlob } from '../service/account-service';

/** Account: Google link (keeps the same uid via linkWithPopup), export, delete. */
export function AccountPanel() {
  const services = useServices();
  const identity = useAppStore((s) => s.identity);
  const setIdentity = useAppStore((s) => s.setIdentity);
  const showToast = useAppStore((s) => s.showToast);
  const [busy, setBusy] = useState(false);

  async function link() {
    const result = await services.auth.linkGoogle();
    if (result.ok) setIdentity(result.value);
    else if (result.error !== 'POPUP_CLOSED') showToast(MESSAGES.errors.generic);
  }

  async function remove() {
    if (window.prompt(MESSAGES.settings.confirmDelete) !== 'DELETE') return;
    setBusy(true);
    try {
      await deleteAccount(services);
      window.location.assign('/');
    } catch {
      showToast(MESSAGES.errors.generic);
      setBusy(false);
    }
  }

  return (
    <Card aria-labelledby="account-heading" className="flex flex-col gap-3">
      <h2 id="account-heading" className="font-bold">
        {MESSAGES.settings.account}
      </h2>
      {identity?.anonymous !== false ? (
        <>
          <p className="text-sm text-muted">{MESSAGES.settings.anonymous}</p>
          <Button variant="secondary" onClick={() => void link()}>
            {MESSAGES.settings.linkGoogle}
          </Button>
        </>
      ) : (
        <p className="text-sm">{MESSAGES.settings.linkedAs(identity.email ?? '')}</p>
      )}
      <Button
        variant="secondary"
        onClick={() => void buildExport(services).then((blob) => downloadBlob(blob, `kpakam-${new Date().toISOString().slice(0, 10)}.json`))}
      >
        {MESSAGES.settings.exportData}
      </Button>
      <Button variant="danger" disabled={busy} onClick={() => void remove()}>
        {MESSAGES.settings.deleteAccount}
      </Button>
    </Card>
  );
}
