import type { ReactNode } from 'react';

const TINTS = [
  'bg-[#e3f1e8] text-[#0f5132]',
  'bg-[#fbf0d4] text-[#7a4f00]',
  'bg-[#e4ecf8] text-[#1d4f91]',
  'bg-[#f6e4ef] text-[#8a2c5c]',
  'bg-[#e9e6f8] text-[#4a3a9a]',
];

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

function tintOf(name: string): string {
  let hash = 0;
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) >>> 0;
  return TINTS[hash % TINTS.length] ?? TINTS[0] ?? '';
}

export interface AvatarProps {
  readonly name?: string;
  readonly icon?: ReactNode;
  readonly tone?: string;
  readonly size?: 'md' | 'lg';
}

/** A round avatar: initials (with a stable tint per name) or an icon. Decorative; the row text carries meaning. */
export function Avatar({ name, icon, tone, size = 'md' }: AvatarProps) {
  const box = size === 'lg' ? 'h-16 w-16 text-xl' : 'h-11 w-11 text-sm';
  const colour = tone ?? (name === undefined ? 'bg-surface-2 text-text' : tintOf(name));
  return (
    <span aria-hidden="true" className={`inline-flex shrink-0 items-center justify-center rounded-full font-bold ${box} ${colour}`}>
      {icon ?? (name === undefined ? null : initialsOf(name))}
    </span>
  );
}
