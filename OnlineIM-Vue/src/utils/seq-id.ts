/**
 * 消息 seq_id 的统一比较工具。
 *
 * seq_id 在协议和 IndexedDB 中以字符串保存，但语义是非负整数，不能使用
 * 字符串字典序或 Number（Snowflake 超过安全整数范围）比较。
 */
export type SeqIdValue = string | number | null | undefined

function toBigInt(value: SeqIdValue): bigint {
  const text = value === null || value === undefined || value === ''
    ? '0'
    : String(value).trim()

  // 非法 seq 不应破坏本地历史读取；服务端会拒绝非法回执，这里按 0 处理，
  // 让旧缓存记录不会参与有效游标推进。
  if (!/^\d+$/.test(text)) return 0n
  return BigInt(text)
}

export function compareSeqId(left: SeqIdValue, right: SeqIdValue): number {
  const leftValue = toBigInt(left)
  const rightValue = toBigInt(right)
  return leftValue < rightValue ? -1 : leftValue > rightValue ? 1 : 0
}

export function maxSeqId(left: SeqIdValue, right: SeqIdValue): string {
  const leftValue = toBigInt(left)
  const rightValue = toBigInt(right)
  return (leftValue >= rightValue ? leftValue : rightValue).toString()
}
