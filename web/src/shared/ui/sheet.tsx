import { useEffect, useId, useRef, type ReactNode } from 'react';
import { CloseIcon } from './icons';

export interface SheetProps {
  readonly open: boolean;
  readonly title: string;
  readonly onClose: () => void;
  readonly children: ReactNode;
}

/**
 * A bottom sheet on the native <dialog> element: focus is trapped, Escape closes it, and the page
 * behind is inert. It slides up from the bottom with an iOS-like drawer curve.
 */
export function Sheet({ open, title, onClose, children }: SheetProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (dialog === null) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);
  return (
    // Tapping the dim backdrop closes the sheet; keyboard users have Escape (native) and the Close button.
    // eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-noninteractive-element-interactions
    <dialog
      ref={ref}
      aria-labelledby={titleId}
      onClose={onClose}
      onClick={(event) => event.target === ref.current && onClose()}
      className="m-0 mt-auto max-h-[90dvh] w-full max-w-none bg-transparent p-0 backdrop:bg-black/40 open:flex open:flex-col"
    >
      {open && (
        <div className="sheet-enter mx-auto w-full max-w-2xl rounded-t-[32px] bg-surface px-5 pb-[calc(1.5rem+env(safe-area-inset-bottom))] pt-3 text-text">
          <div aria-hidden="true" className="mx-auto mb-3 h-1.5 w-10 rounded-full bg-surface-3" />
          <div className="mb-4 flex items-center justify-between gap-3">
            <span className="w-12" />
            <h2 id={titleId} className="text-[17px] font-semibold">
              {title}
            </h2>
            <button type="button" onClick={onClose} aria-label="Close" className="press flex h-11 w-11 items-center justify-center rounded-full bg-surface-2">
              <CloseIcon width={20} height={20} />
            </button>
          </div>
          {children}
        </div>
      )}
    </dialog>
  );
}
