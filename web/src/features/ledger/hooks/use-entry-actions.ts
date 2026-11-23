import { useCallback } from 'react';
import { useAppStore } from '@/app/app-store';
import { useServices } from '@/app/services';
import type { Draft } from '@/inference/domain/draft';
import { MESSAGES } from '@/shared/messages/en';
import type { Journal } from '../domain/journal';

/** Undo and Fix (F4). Both write new journals; nothing is ever edited or deleted. */
export function useEntryActions() {
  const { ledger } = useServices();
  const showToast = useAppStore((s) => s.showToast);
  const undo = useCallback(
    async (journal: Journal) => {
      const result = await ledger.reverseEntry(journal);
      showToast(result.ok ? MESSAGES.ledger.reversed : MESSAGES.errors.alreadyReversed);
    },
    [ledger, showToast],
  );
  const fix = useCallback(
    async (journal: Journal, corrected: Draft) => {
      const result = await ledger.fixEntry(journal, corrected);
      showToast(result.ok ? MESSAGES.drafts.saved(1) : MESSAGES.errors.saveFailed);
      return result.ok;
    },
    [ledger, showToast],
  );
  return { undo, fix };
}
