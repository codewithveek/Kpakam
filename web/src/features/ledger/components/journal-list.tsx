import { useVirtualizer } from '@tanstack/react-virtual';
import { useRef } from 'react';
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

function VirtualList({ entries, reversed, customers, label }: JournalListProps) {
  const parent = useRef<HTMLDivElement>(null);
  // TanStack Virtual returns mutable helpers; we do not use the React Compiler, so this is safe.
  // eslint-disable-next-line react-hooks/incompatible-library
  const virtualizer = useVirtualizer({ count: entries.length, getScrollElement: () => parent.current, estimateSize: () => 104, overscan: 6 });
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

/** Journal entries; long lists are virtualised (TanStack Virtual). */
export function JournalList(props: JournalListProps) {
  if (props.entries.length > VIRTUALISE_AFTER) return <VirtualList {...props} />;
  return (
    <ul aria-label={props.label} className="flex flex-col gap-2">
      {props.entries.map((entry) => (
        <JournalRow key={entry.journal.id} entry={entry} reversed={props.reversed.has(entry.journal.id)} customerName={nameOf(entry, props.customers)} />
      ))}
    </ul>
  );
}
