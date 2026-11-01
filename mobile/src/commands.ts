/** One key for the unchanged action, retained after uncertain failures. No automatic retries. */
export class CommandKey {
  private key: string | undefined;
  private signature: string | undefined;
  constructor(private readonly uuid: () => string) {}
  forPayload(payload: unknown): string {
    const signature = JSON.stringify(canonical(payload));
    if (this.signature !== signature || !this.key) { this.signature = signature; this.key = this.uuid(); }
    return this.key;
  }
  changed(): void { this.key = undefined; this.signature = undefined; }
  succeeded(): void { this.changed(); }
}

function canonical(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonical);
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).sort(([a], [b]) => a.localeCompare(b)).map(([k, v]) => [k, canonical(v)]));
  return value;
}
