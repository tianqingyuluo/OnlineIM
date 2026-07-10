import { describe, expect, it } from 'vitest'
import {
  advanceDeliveryState,
  type DeliveryState,
} from '@/utils/message-state'

describe('advanceDeliveryState', () => {
  it.each([
    ['sending', 'sent', 'sent'],
    ['sent', 'delivered', 'delivered'],
    ['delivered', 'read', 'read'],
    ['failed', 'sending', 'sending'],
  ] as Array<[DeliveryState, Parameters<typeof advanceDeliveryState>[1], DeliveryState]>)
  ('moves %s through the accepted event %s', (current, event, expected) => {
    expect(advanceDeliveryState(current, event)).toBe(expected)
  })

  it('does not allow a late event to downgrade a confirmed state', () => {
    expect(advanceDeliveryState('read', 'sent')).toBe('read')
    expect(advanceDeliveryState('delivered', 'sent')).toBe('delivered')
    expect(advanceDeliveryState('failed', 'delivered')).toBe('delivered')
  })

  it('keeps failure visible until the user explicitly retries', () => {
    expect(advanceDeliveryState('failed', 'failed')).toBe('failed')
    expect(advanceDeliveryState('failed', 'retry')).toBe('sending')
  })
})
