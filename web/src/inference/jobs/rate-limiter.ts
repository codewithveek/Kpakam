/** A sliding-window limiter for cloud calls (PRD "Abuse and cost"). */
export interface RateLimiter {
  /** Records a call and returns true, or returns false when the limit is reached. */
  tryAcquire(now?: number): boolean;
}

const WINDOW_MS = 60_000;

/** Creates a per-minute limiter. `limit` comes from Remote Config. */
export function createRateLimiter(limit: () => number): RateLimiter {
  const calls: number[] = [];
  return {
    tryAcquire(now = Date.now()) {
      while (calls.length > 0 && (calls[0] ?? 0) <= now - WINDOW_MS) calls.shift();
      if (calls.length >= limit()) return false;
      calls.push(now);
      return true;
    },
  };
}
