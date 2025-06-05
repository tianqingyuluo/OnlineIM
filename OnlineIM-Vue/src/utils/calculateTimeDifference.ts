export function calculateMinutesDifference(start: number | string, end: number | string): number {
  const startTime = typeof start === 'string' ? new Date(start).getTime() : start;
  const endTime = typeof end === 'string' ? new Date(end).getTime() : end;
  const differenceInMilliseconds = endTime - startTime;
  const differenceInMinutes = differenceInMilliseconds / (1000 * 60);
  return differenceInMinutes;
}