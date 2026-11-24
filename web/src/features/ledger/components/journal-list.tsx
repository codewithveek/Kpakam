import { useVirtualizer } from '@tanstack/react-virtual';
import { useRef, useState } from 'react';
import { decodeTime } from 'ulid';
import { ListGroup } from '@/shared/ui/list';
import type { Customer } from '../domain/customer';
import type { JournalEntry } from '../repository/journal-repository';
import { JournalRow } from './journal-row';

const VIRTUALISE_AFTER = 40;

export interface JournalListProps {
  readonly entries: readonly JournalEntry[];
  readonly reversed: ReadonlySet<string>;
  readonly customers: ReadonlyMap<string, Customer>;
  readonly label: string;
}

function nameOf(entry: JournalEntry, customers: ReadonlyMap<string, Customer>): string | null {
  return entry.journal.customerId === null ? null : (customers.get(entry.journal.customerId)?.name ?? null);
}

/** Entries created after this list mounted (ULIDs carry their creation time). */
function useIsNew(): (id: string) => boolean {
  const [mountedAt] = useState(() => Date.now());
  return (id) => {
    try {
      return decodeTime(id) > mountedAt;
    } catch {
      return false;
    }
  };
}

function VirtualList({ entries, reversed, customers, label }: JournalListProps) {
  const parent = useRef<HTMLDivElement>(null);
  // TanStack Virtual returns mutable helpers; we do not use the React Compiler, so this is safe.
  // eslint-disable-next-line react-hooks/incompatible-library
  const virtualizer = useVirtualizer({ count: entries.length, getScrollElement: () => parent.current, estimateSize: () => 72, overscan: 6 });
  return (
    <div ref={parent} className="max-h-[70vh] overflow-y-auto">
      <ul aria-label={label} className="relative" style={{ height: virtualizer.getTotalSize() }}>
        {virtualizer.getVirtualItems().map((item) => {
          const entry = entries[item.index];
          if (entry === undefined) return null;
          return (
            <div
              key={entry.journal.id}
              ref={virtualizer.measureElement}
              data-index={item.index}
              className="absolute inset-x-0 pb-2"
              style={{ transform: `translateY(${item.start}px)` }}
            >
              <JournalRow entry={entry} reversed={reversed.has(entry.journal.id)} customerName={nameOf(entry, customers)} />
            </div>
          );
        })}
      </ul>
    </div>
  );
}

/** Entries as one grouped list; long lists are virtualised (TanStack Virtual). */
export function JournalList(props: JournalListProps) {
  const isNew = useIsNew();
  if (props.entries.length === 0) return null;
  if (props.entries.length > VIRTUALISE_AFTER) return <VirtualList {...props} />;
  return (
    <ListGroup aria-label={props.label}>
      {props.entries.map((entry) => (
        <JournalRow
          key={entry.journal.id}
          entry={entry}
          reversed={props.reversed.has(entry.journal.id)}
          customerName={nameOf(entry, props.customers)}
          isNew={isNew(entry.journal.id)}
        />
      ))}
    </ListGroup>
  );
}
