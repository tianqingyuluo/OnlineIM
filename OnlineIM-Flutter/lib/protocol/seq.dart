/// `seq_id` 按非负整数比较，禁止字典序。
int compareSeq(String? left, String? right) {
  final a = BigInt.tryParse((left ?? '').trim()) ?? BigInt.zero;
  final b = BigInt.tryParse((right ?? '').trim()) ?? BigInt.zero;
  return a.compareTo(b);
}

bool seqGreater(String? left, String? right) => compareSeq(left, right) > 0;

String maxSeq(String? left, String? right) => compareSeq(left, right) >= 0 ? (left ?? '0') : (right ?? '0');

const deliveryRank = {
  'failed': 0,
  'sending': 1,
  'sent': 2,
  'delivered': 3,
  'read': 4,
};

/// ACK / 回执乱序时不能把状态降级。
String mergeDeliveryState(String? current, String? incoming) {
  final currentRank = deliveryRank[current] ?? 0;
  final incomingRank = deliveryRank[incoming] ?? 0;
  if (incomingRank >= currentRank && incoming != null && incoming.isNotEmpty) {
    return incoming;
  }
  return current ?? incoming ?? 'sending';
}
