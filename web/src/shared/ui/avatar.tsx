import type { ReactNode } from 'react';

function initialsOf(name: string): string {
  const all = name.split(/\s+/).filter(Boolean);
  const named = all.filter((p) => !/^(mama|iya|baba|oga|madam|mr|mrs)$/i.test(p));
  const parts = named.length >= 2 ? named : all;
  return (
    parts
      .slice(0, 2)
      .map((p) => p.charAt(0).toUpperCase())
      .join('') || '?'
  );
}

export interface AvatarProps {
  readonly name?: string;
  readonly icon?: ReactNode;
  readonly tone?: string;
  readonly size?: 'md' | 'lg';
}

/** A monochrome tile: initials for people, an icon for entries. Decorative; the row text carries meaning. */
export function Avatar({ name, icon, tone, size = 'md' }: AvatarProps) {
  const box = size === 'lg' ? 'h-20 w-20 rounded-[26px] text-2xl' : 'h-11 w-11 rounded-[14px] text-[14px]';
  return (
    <span
      aria-hidden="true"
      className={`inline-flex shrink-0 items-center justify-center font-semibold tracking-[-0.01em] ${box} ${tone ?? 'bg-surface-2 text-text'}`}
    >
      {icon ?? (name === undefined ? null : initialsOf(name))}
    </span>
  );
}
