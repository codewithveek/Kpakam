import { describe, expect, it } from 'vitest';
import { newCustomerId } from '@/shared/ids/ulid';
import { asKobo } from '@/shared/money/kobo';
import { customerNameKey, findCustomerByName, sortByDebt, type Customer } from './customer';

const c = (name: string, balance: number): Customer => ({
  id: newCustomerId(),
  name,
  nameKey: customerNameKey(name),
  phone: null,
  balanceKobo: asKobo(balance),
});

describe('customers', () => {
  it('normalises names for matching', () => {
    expect(customerNameKey('  Mama   BISI ')).toBe('mama bisi');
    const bisi = c('Mama Bisi', 0);
    expect(findCustomerByName([bisi], 'mama  bisi')).toBe(bisi);
    expect(findCustomerByName([bisi], 'Iya Bisi')).toBeNull();
  });

  it('sorts by debt, then by name', () => {
    const sorted = sortByDebt([c('Tunde', 0), c('Ada', 500), c('Bola', 500), c('Chidi', -100)]);
    expect(sorted.map((x) => x.name)).toEqual(['Ada', 'Bola', 'Tunde', 'Chidi']);
  });
});
