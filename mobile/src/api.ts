import type { Usage } from './types';
import type { SessionState } from './session';
export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string, public usage?: Usage) { super(message); }
}
export function apiOrigin(raw: string): string {
  try {
    const u = new URL(raw);
    if (!['https:', 'http:'].includes(u.protocol) || u.username || u.password || u.search || u.hash || (u.pathname !== '/' && u.pathname !== '')) throw new Error();
    return u.origin;
  } catch { throw new Error('앱의 서버 주소 설정을 확인해 주세요.'); }
}
const errorText = (status: number, code: string) => {
  if (status === 401) return '로그인 정보가 만료되었어요. 다시 로그인해 주세요.';
  if (status === 403) return '이 사업장에 접근할 수 없어요. 계정을 확인해 주세요.';
  if (code === 'RECEIVABLE_DUPLICATE') return '이미 등록된 거래처 코드 또는 청구 번호예요. 장부를 먼저 확인해 주세요.';
  if (code === 'MATCHING_MONTHLY_LIMIT') return '이번 달 후보 생성 한도를 모두 사용했어요. 기존 결과 조회·연결·취소는 계속 이용할 수 있어요. 거절된 실행은 차감되지 않아요.';
  if (code === 'MATCHING_MONTH_CHANGED') return '서버에서 이용 월이 바뀌었어요. 차감되지 않았어요. 사용량을 새로 읽고 같은 내용으로 다시 눌러 주세요.';
  if (code === 'MATCHING_SOURCE_LIMIT') return '검토할 자료가 실행 상한을 넘었어요. 입금 기간을 줄여 주세요. 오래된 미수 청구도 상한에 포함돼요. 실패 실행은 차감되지 않아요.';
  if (code === 'ALLOCATION_EXCEEDS_AVAILABLE') return '현재 잔액이 부족해 연결하지 못했어요. 초안을 보존했으니 현재 잔액을 다시 불러와 금액을 검토해 주세요.';
  if (code === 'ALLOCATION_ALREADY_CANCELLED') return '이미 취소된 연결이에요. 현재 이력을 다시 불러와 주세요.';
  if (code === 'IDEMPOTENCY_CONFLICT') return '같은 요청 키의 이전 내용과 달라요. 현재 이력을 조회하고 입력 내용을 확인해 주세요.';
  if (status === 400) return '입력한 내용을 확인해 주세요. 날짜, 금액과 필수 항목을 다시 살펴봐 주세요.';
  if (status === 409) return '이미 사용 중인 정보예요. 입력 내용과 기존 등록을 확인해 주세요.';
  if (status === 404) return '자료를 찾을 수 없어요. 목록을 새로고침해 주세요.';
  if (status === 429) return '요청이 많아요. 잠시 후 다시 시도해 주세요.';
  return '서버 응답을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.';
};
export class Api {
  constructor(private origin: string, private session: SessionState, private fetcher: typeof fetch = (...args) => fetch(...args)) {}
  async revoke(): Promise<void> {
    const token = this.session.token;
    const abort = new AbortController();
    const timer = setTimeout(() => abort.abort(), 12000);
    try {
      const response = await this.fetcher(apiOrigin(this.origin) + '/api/v1/auth/logout', { method: 'POST', signal: abort.signal, headers: { Authorization: `Bearer ${token}` } });
      if (!response.ok && response.status !== 401) throw new Error();
    } catch { throw new Error('기기에서는 로그아웃했지만 서버 세션 종료를 확인하지 못했어요.'); }
    finally { clearTimeout(timer); }
  }
  async request<T>(path: string, options: { method?: string; body?: unknown; key?: string; anonymous?: boolean } = {}): Promise<T> {
    const base = apiOrigin(this.origin);
    const epoch = this.session.epoch;
    const token = options.anonymous ? null : this.session.token;
    const abort = new AbortController();
    const timer = setTimeout(() => abort.abort(), 12000);
    try {
      const response = await this.fetcher(base + '/api/v1' + path, {
        method: options.method ?? 'GET', signal: abort.signal,
        headers: { Accept: 'application/json', ...(options.body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(options.key ? { 'Idempotency-Key': options.key } : {}) },
        ...(options.body !== undefined ? { body: JSON.stringify(options.body) } : {}),
      });
      this.session.assert(epoch);
      if (!response.ok) {
        const error = await response.json().catch(() => ({})) as { code?: string; usage?: Usage };
        this.session.assert(epoch);
        if (response.status === 401 && !options.anonymous) this.session.expire(epoch);
        const code = typeof error.code === 'string' ? error.code : '';
        throw new ApiError(response.status, code, errorText(response.status, code), error.usage);
      }
      if (response.status === 204) return undefined as T;
      const result = await response.json() as T;
      this.session.assert(epoch);
      return result;
    } catch (error) {
      this.session.assert(epoch);
      if (error instanceof ApiError) throw error;
      throw new ApiError(0, 'TRANSPORT', '응답을 확인하지 못했어요. 연결을 확인한 뒤 다시 시도해 주세요.');
    } finally { clearTimeout(timer); }
  }
}
