/** One key for the unchanged action, retained after uncertain failures. No automatic retries. */
export class CommandKey {
  private key: string | undefined;
  private signature: string | undefined;
  constructor(private readonly uuid: () => string) {}
  forPayload(payload: unknown): string {
    const signature = JSON.stringify(payload);
    if (this.signature !== signature || !this.key) { this.signature = signature; this.key = this.uuid(); }
    return this.key;
  }
  changed(): void { this.key = undefined; this.signature = undefined; }
  succeeded(): void { this.changed(); }
}
