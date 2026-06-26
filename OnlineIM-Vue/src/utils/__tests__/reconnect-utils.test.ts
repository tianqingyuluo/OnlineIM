import { describe, it, expect } from 'vitest'
import {
  computeBackoff,
  computeBackoffBase,
  shouldTransitionToOffline,
  isValidTransition,
  OFFLINE_THRESHOLD,
  OUTBOUND_QUEUE_LIMIT,
  MAX_BACKOFF_MS,
  type ConnectionState,
} from '@/utils/reconnect-utils'

describe('computeBackoffBase', () => {
  it('returns 1s for attempt 0', () => {
    expect(computeBackoffBase(0)).toBe(1_000)
  })

  it('returns 2s for attempt 1', () => {
    expect(computeBackoffBase(1)).toBe(2_000)
  })

  it('returns 4s for attempt 2', () => {
    expect(computeBackoffBase(2)).toBe(4_000)
  })

  it('returns 8s for attempt 3', () => {
    expect(computeBackoffBase(3)).toBe(8_000)
  })

  it('returns 16s for attempt 4', () => {
    expect(computeBackoffBase(4)).toBe(16_000)
  })

  it('returns 30s for attempt 5', () => {
    expect(computeBackoffBase(5)).toBe(30_000)
  })

  it('caps at 30s for attempt > 5', () => {
    expect(computeBackoffBase(10)).toBe(30_000)
    expect(computeBackoffBase(100)).toBe(30_000)
  })
})

describe('computeBackoff', () => {
  it('returns value within ±25% jitter of base', () => {
    for (let attempt = 0; attempt < 6; attempt++) {
      const base = computeBackoffBase(attempt)
      const min = Math.round(base * 0.75)
      const max = Math.round(base * 1.25)
      for (let i = 0; i < 50; i++) {
        const result = computeBackoff(attempt)
        expect(result).toBeGreaterThanOrEqual(min)
        expect(result).toBeLessThanOrEqual(max)
      }
    }
  })

  it('never exceeds MAX_BACKOFF_MS', () => {
    for (let i = 0; i < 100; i++) {
      expect(computeBackoff(100)).toBeLessThanOrEqual(MAX_BACKOFF_MS)
    }
  })
})

describe('shouldTransitionToOffline', () => {
  it('returns false for attempts below threshold', () => {
    expect(shouldTransitionToOffline(0)).toBe(false)
    expect(shouldTransitionToOffline(OFFLINE_THRESHOLD - 1)).toBe(false)
  })

  it('returns true at threshold', () => {
    expect(shouldTransitionToOffline(OFFLINE_THRESHOLD)).toBe(true)
  })

  it('returns true above threshold', () => {
    expect(shouldTransitionToOffline(OFFLINE_THRESHOLD + 1)).toBe(true)
    expect(shouldTransitionToOffline(100)).toBe(true)
  })
})

describe('isValidTransition', () => {
  const validCases: Array<[ConnectionState, ConnectionState]> = [
    ['connecting', 'online'],
    ['connecting', 'reconnecting'],
    ['online', 'reconnecting'],
    ['online', 'offline'],
    ['reconnecting', 'online'],
    ['reconnecting', 'offline'],
    ['offline', 'reconnecting'],
    ['offline', 'online'],
  ]

  for (const [from, to] of validCases) {
    it(`allows ${from} → ${to}`, () => {
      expect(isValidTransition(from, to)).toBe(true)
    })
  }

  const invalidCases: Array<[ConnectionState, ConnectionState]> = [
    ['online', 'connecting'],
    ['online', 'online'],
    ['offline', 'connecting'],
  ]

  for (const [from, to] of invalidCases) {
    it(`rejects ${from} → ${to}`, () => {
      expect(isValidTransition(from, to)).toBe(false)
    })
  }
})

describe('constants', () => {
  it('OUTBOUND_QUEUE_LIMIT is 50', () => {
    expect(OUTBOUND_QUEUE_LIMIT).toBe(50)
  })

  it('OFFLINE_THRESHOLD is 5', () => {
    expect(OFFLINE_THRESHOLD).toBe(5)
  })

  it('MAX_BACKOFF_MS is 30000', () => {
    expect(MAX_BACKOFF_MS).toBe(30_000)
  })
})
