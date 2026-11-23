import type { ReactNode } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import { MESSAGES } from '@/shared/messages/en';
import { ChevronRightIcon, DownloadIcon, LinkIcon, TrashIcon } from '@/shared/ui/icons';
import { ListGroup } from '@/shared/ui/list';
import { buildExport, deleteAccount, downloadBlob } from '../service/account-service';

function ActionRow({
  icon,
  label,
  onClick,
  danger = false,
}: {
  readonly icon: ReactNode;
  readonly label: string;
  readonly onClick: () => void;
  readonly danger?: boolean;
}) {
  return (
    <li>
      <button
        type="button"
        onClick={onClick}
        className={`press flex min-h-14 w-full items-center gap-3 px-4 text-left text-[15px] font-semibold active:bg-surface-2 ${danger ? 'text-danger' : ''}`}
      >
        <span className={`flex h-9 w-9 items-center justify-center rounded-full ${danger ? 'bg-danger-soft' : 'bg-surface-2'}`}>{icon}</span>
        <span className="flex-1">{label}</span>
        {!danger && <ChevronRightIcon width={18} height={18} className="text-muted" />}
      </button>
    </li>
  );
}

/** Account: Google link (keeps the same uid via linkWithPopup), export, delete. */
export function AccountPanel() {
  const services = useServices();
  const identity = useAppStore((s) => s.identity);
  const setIdentity = useAppStore((s) => s.setIdentity);
  const showToast = useAppStore((s) => s.showToast);

  async function link() {
    const result = await services.auth.linkGoogle();
    if (result.ok) setIdentity(result.value);
    else if (result.error !== 'POPUP_CLOSED') showToast(MESSAGES.errors.generic);
  }
  async function remove() {
    if (window.prompt(MESSAGES.settings.confirmDelete) !== 'DELETE') return;
    try {
      await deleteAccount(services);
      window.location.assign('/');
    } catch {
      showToast(MESSAGES.errors.generic);
    }
  }
  const exportData = () => void buildExport(services).then((blob) => downloadBlob(blob, `kpakam-${new Date().toISOString().slice(0, 10)}.json`));

  return (
    <div className="flex flex-col gap-2">
      <p className="px-1 text-[13px] leading-snug text-muted">
        {identity?.anonymous !== false ? MESSAGES.settings.anonymous : MESSAGES.settings.linkedAs(identity.email ?? '')}
      </p>
      <ListGroup>
        {identity?.anonymous !== false && (
          <ActionRow icon={<LinkIcon width={18} height={18} />} label={MESSAGES.settings.linkGoogle} onClick={() => void link()} />
        )}
        <ActionRow icon={<DownloadIcon width={18} height={18} />} label={MESSAGES.settings.exportData} onClick={exportData} />
        <ActionRow icon={<TrashIcon width={18} height={18} />} label={MESSAGES.settings.deleteAccount} onClick={() => void remove()} danger />
      </ListGroup>
    </div>
  );
}
