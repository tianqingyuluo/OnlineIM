export type DeliveryState = 'sending' | 'failed' | 'sent' | 'delivered' | 'read'

export type DeliveryStateEvent = DeliveryState | 'retry'

const STATE_ORDER: Record<DeliveryState, number> = {
  sending: 0,
  failed: 0,
  sent: 1,
  delivered: 2,
  read: 3,
}

/**
 * 将服务端回执折叠为单调的消息状态。
 * 失败状态只有用户主动重试或收到更高等级的服务端回执才会离开。
 */
export function advanceDeliveryState(
  current: DeliveryState,
  event: DeliveryStateEvent,
): DeliveryState {
  if (event === 'retry') return current === 'failed' ? 'sending' : current
  if (event === 'failed') return STATE_ORDER[current] >= STATE_ORDER.sent ? current : 'failed'
  return STATE_ORDER[event] >= STATE_ORDER[current] ? event : current
}

export function isTerminalDeliveryState(state: DeliveryState): boolean {
  return state === 'read'
}
