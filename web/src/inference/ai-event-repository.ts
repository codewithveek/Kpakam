import { collection, doc, onSnapshot, query, serverTimestamp, setDoc, Timestamp, where, type Firestore } from 'firebase/firestore';
import { newUlid } from '@/shared/ids/ulid';
import type { AiEventRecord, AiEventSink } from './ai-event';

/** Weekly routing numbers for the Stats screen (F6). */
export interface RoutingStats {
  readonly onDevice: number;
  readonly cloud: number;
  /** Successful on-device runs: each one is a cloud call the trader did not pay for. */
  readonly cloudCallsAvoided: number;
  readonly onDeviceShare: number | null;
}

/** Summarises events into stats. Pure. */
export function summariseEvents(events: readonly Pick<AiEventRecord, 'source' | 'valid'>[]): RoutingStats {
  const onDevice = events.filter((e) => e.source === 'on_device' && e.valid).length;
  const cloud = events.filter((e) => e.source === 'in_cloud' && e.valid).length;
  const total = onDevice + cloud;
  return { onDevice, cloud, cloudCallsAvoided: onDevice, onDeviceShare: total === 0 ? null : onDevice / total };
}

export interface RoutingStatsSource {
  /** Live stats since a date; events appear once their server timestamp resolves. */
  watchStats(since: Date, onChange: (stats: RoutingStats) => void): () => void;
}

/** Writes `aiEvents` (codes and numbers only) and reads weekly stats. */
export function createFirestoreAiEvents(db: Firestore, uid: string): AiEventSink & RoutingStatsSource {
  const events = collection(db, 'users', uid, 'aiEvents');
  return {
    record(event) {
      const id = newUlid();
      void setDoc(doc(events, id), { id, ...event, createdAt: serverTimestamp(), schemaVersion: 1 }).catch((error: unknown) => {
        console.warn('aiEvent not stored', error);
      });
    },
    watchStats(since, onChange) {
      const q = query(events, where('createdAt', '>=', Timestamp.fromDate(since)));
      return onSnapshot(q, (snapshot) => {
        onChange(summariseEvents(snapshot.docs.map((d) => ({ source: d.get('source') as AiEventRecord['source'], valid: d.get('valid') === true }))));
      });
    },
  };
}
