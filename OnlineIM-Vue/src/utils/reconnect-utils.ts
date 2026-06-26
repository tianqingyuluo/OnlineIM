export type ConnectionState = 'connecting' | 'online' | 'reconnecting' | 'offline';

export const HEARTBEAT_INTERVAL_MS = 30_000;
export const ACK_MISS_THRESHOLD = 2;
export const OFFLINE_THRESHOLD = 5;
export const MAX_BACKOFF_MS = 30_000;
export const OUTBOUND_QUEUE_LIMIT = 50;

const BACKOFF_STEPS = [1_000, 2_000, 4_000, 8_000, 16_000, 30_000];

export function computeBackoff(attempt: number): number {
  const base = BACKOFF_STEPS[Math.min(attempt, BACKOFF_STEPS.length - 1)];
  const jitter = base * 0.25 * (Math.random() * 2 - 1);
  return Math.min(Math.round(base + jitter), MAX_BACKOFF_MS);
}

export function computeBackoffBase(attempt: number): number {
  return BACKOFF_STEPS[Math.min(attempt, BACKOFF_STEPS.length - 1)];
}

export function shouldTransitionToOffline(attempt: number): boolean {
  return attempt >= OFFLINE_THRESHOLD;
}

export type Transition =
  | { from: ConnectionState; to: ConnectionState; valid: boolean };

const VALID_TRANSITIONS: Record<ConnectionState, ConnectionState[]> = {
  connecting: ['online', 'reconnecting', 'offline'],
  online: ['reconnecting', 'offline'],
  reconnecting: ['online', 'offline', 'reconnecting'],
  offline: ['reconnecting', 'online'],
};

export function isValidTransition(from: ConnectionState, to: ConnectionState): boolean {
  return VALID_TRANSITIONS[from]?.includes(to) ?? false;
}
