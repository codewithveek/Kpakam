import defaults from '@contract/remote-config.defaults.json';
import routingCases from '@contract/routing-cases.json';
import { describe, expect, it } from 'vitest';
import { escalate, routeJob, routeSpeech, type RouteConfig, type RouteSignals, type SpeechSignals } from './route-decision';

type ConfigOverride = Partial<Record<'route_max_device_chars' | 'route_language_threshold' | 'route_min_battery', number>>;

function configFor(override: ConfigOverride = {}): RouteConfig {
  const merged = { ...defaults, ...override };
  return {
    maxDeviceChars: merged.route_max_device_chars,
    languageThreshold: merged.route_language_threshold,
    minBattery: merged.route_min_battery,
  };
}

describe('contract: routing-cases.json (text)', () => {
  it.each(routingCases.text)('$name', (testCase) => {
    const signals: RouteSignals = { ...routingCases.defaultSignals, ...testCase.signals } as RouteSignals;
    const config = configFor('config' in testCase ? testCase.config : {});
    expect(routeJob(signals, config)).toEqual(testCase.expected);
  });
});

describe('contract: routing-cases.json (speech)', () => {
  it.each(routingCases.speech)('$name', (testCase) => {
    const result = routeSpeech(testCase.signals as SpeechSignals);
    expect(result).toEqual({ confidence: 'normal', ...testCase.expected });
  });
});

describe('escalate', () => {
  it('escalates to cloud when reachable', () => {
    expect(escalate({ online: true, cloudEnabled: true })).toMatchObject({ target: 'cloud', reason: 'VALIDATION_ESCALATION' });
  });
  it('queues when offline or when the cloud is paused', () => {
    expect(escalate({ online: false, cloudEnabled: true }).queueReason).toBe('OFFLINE');
    expect(escalate({ online: true, cloudEnabled: false }).queueReason).toBe('CLOUD_DISABLED');
  });
});
