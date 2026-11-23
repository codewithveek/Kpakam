import type { BatteryState } from '@/inference/domain/route-decision';

interface BatteryManagerLike {
  readonly level: number;
  readonly charging: boolean;
}

/** Reads the Battery API where it exists; returns null elsewhere (routing.md). */
export async function readBattery(nav: Navigator = navigator): Promise<BatteryState | null> {
  const getBattery: unknown = Reflect.get(nav, 'getBattery');
  if (typeof getBattery !== 'function') return null;
  try {
    const battery = await (getBattery as () => Promise<BatteryManagerLike>).call(nav);
    return { level: battery.level, charging: battery.charging };
  } catch {
    return null;
  }
}
