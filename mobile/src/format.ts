// Decimal strings never pass through JavaScript Number, including server totals.
export function money(value: string): string {
  if (!/^-?\d+$/.test(value)) return '금액 확인 필요';
  const n = BigInt(value);
  return `${n < 0n ? '−' : ''}${(n < 0n ? -n : n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')}원`;
}
export function koreaToday(now = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  const get = (t: string) => parts.find(p => p.type === t)!.value;
  return `${get('year')}-${get('month')}-${get('day')}`;
}
export function validDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [y, m, d] = value.split('-').map(Number);
  if (y < 1 || m < 1 || m > 12 || d < 1) return false;
  const days = [31, y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  return d <= days[m - 1];
}
export function validAmount(value: string): boolean {
  return /^[1-9]\d{0,12}$/.test(value) && BigInt(value) <= 1000000000000n;
}
export const dateLabel = (value: string) => value.replaceAll('-', '.');
export const statusLabel = (status: string) => ({ UNPAID: '입금 미연결', PARTIAL: '일부 연결', PAID: '입금 연결 완료', UNALLOCATED: '미연결', ALLOCATED: '전체 연결' }[status] ?? '상태 확인 필요');
