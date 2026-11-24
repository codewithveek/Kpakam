import { describe, expect, it } from 'vitest';
import { summariseEvents } from './ai-event-repository';

describe('summariseEvents', () => {
  it('counts valid runs per source and the on-device share', () => {
    const stats = summariseEvents([
      { source: 'on_device', valid: true },
      { source: 'on_device', valid: true },
      { source: 'on_device', valid: false },
      { source: 'in_cloud', valid: true },
      { source: 'none', valid: false },
    ]);
    expect(stats).toEqual({ onDevice: 2, cloud: 1, cloudCallsAvoided: 2, onDeviceShare: 2 / 3 });
    expect(summariseEvents([]).onDeviceShare).toBeNull();
  });
});
