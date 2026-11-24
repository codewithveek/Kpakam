import { useEffect, useMemo, useState } from 'react';
import { useServices } from '@/app/services';
import { sortByDebt, type Customer } from '../domain/customer';

/** Live debt book, sorted by debt, plus a lookup by id. */
export function useCustomers() {
  const { customers } = useServices();
  const [list, setList] = useState<readonly Customer[] | null>(null);
  useEffect(() => customers.watchAll(setList), [customers]);
  return useMemo(() => {
    const sorted = sortByDebt(list ?? []);
    return { customers: sorted, byId: new Map(sorted.map((c) => [c.id as string, c])), loading: list === null };
  }, [list]);
}
