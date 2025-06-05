export function isTimestampEqual(timestamp1: string, timestamp2: string, allowedDiffMs: number = 1000): boolean {
    const time1 = new Date(timestamp1).getTime();
    const time2 = new Date(timestamp2).getTime();
    return Math.abs(time1 - time2) <= allowedDiffMs;
  }