export type TokenStorage = { read(): Promise<string | null>; write(value: string): Promise<void>; clear(): Promise<void> };
export class StaleSession extends Error {}
/** Epoch checks fence all delayed responses, including an old session's 401. */
export class SessionState {
  token: string | null = null;
  epoch = 0;
  private writes: Promise<void> = Promise.resolve();
  constructor(private storage: TokenStorage, private onExpired: () => void) {}
  async restore(): Promise<void> {
    const epoch = this.epoch;
    const token = await this.storage.read();
    if (epoch === this.epoch) { this.token = token; this.epoch++; }
  }
  async set(token: string): Promise<void> {
    this.epoch++; this.token = token;
    const write = this.writes.then(() => this.storage.write(token));
    this.writes = write.catch(() => {});
    await write;
  }
  clear(): Promise<void> {
    this.epoch++; this.token = null;
    const write = this.writes.then(() => this.storage.clear());
    this.writes = write.catch(() => {});
    return write;
  }
  assert(epoch: number): void { if (epoch !== this.epoch) throw new StaleSession(); }
  expire(epoch: number): void {
    if (epoch !== this.epoch) return;
    void this.clear().catch(() => {}); this.onExpired();
  }
}
