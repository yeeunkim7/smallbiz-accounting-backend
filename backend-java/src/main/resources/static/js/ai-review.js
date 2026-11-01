(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const BASE = "/api/v1/ai-review";
  const state = {
    date: null,
    summary: null,
    kind: "business",
    page: 0,
    size: 10,
    total: 0,
    generation: 0,
    controller: null,
    aiController: null,
    originalController: null,
    configured: false,
    loading: false,
    needsReload: false,
  };
  const label = {
    IN: "입금",
    OUT: "출금",
    CHARGE_EXPECTED: "충전 예정",
    SETTLEMENT_EXPECTED: "정산 예정",
    REFUND_EXPECTED: "환불 예정",
    USAGE: "이용",
    BUSINESS: "업무",
    BANK: "은행",
    UNREVIEWED: "미검토",
    REVIEWED: "검토 완료",
    NEEDS_RECHECK: "재확인 필요",
  };
  function el(tag, className, text) {
    const n = document.createElement(tag);
    if (className) n.className = className;
    if (text !== undefined) n.textContent = String(text);
    return n;
  }
  function clear(n) {
    n.replaceChildren();
    return n;
  }
  function money(raw, signed = false) {
    try {
      const n = BigInt(raw);
      return (
        (n < 0n ? "−" : signed && n > 0n ? "+" : "") +
        (n < 0n ? -n : n).toLocaleString("ko-KR")
      );
    } catch {
      return "—";
    }
  }
  function amountNode(raw, className, signed = false) {
    const n = el("div", className, money(raw, signed));
    n.append(el("small", "", "원"));
    return n;
  }
  function humanDate(raw) {
    return raw
      ? raw.replace(/^(\d{4})-(\d{2})-(\d{2})$/, "$1년 $2월 $3일")
      : "";
  }
  function shortDate(raw) {
    return raw ? raw.slice(0, 10).replaceAll("-", ".") : "—";
  }
  function today() {
    const parts = new Intl.DateTimeFormat("en-CA", {
      timeZone: "Asia/Seoul",
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
    }).formatToParts(new Date());
    const p = Object.fromEntries(parts.map((x) => [x.type, x.value]));
    return `${p.year}-${p.month}-${p.day}`;
  }
  async function request(path, signal, options = {}) {
    const boundedSignal = AbortSignal.any([
      ...(signal ? [signal] : []),
      AbortSignal.timeout(options.method === "POST" ? 145000 : 20000),
    ]);
    const r = await fetch(BASE + path, {
      ...options,
      signal: boundedSignal,
      headers: {
        Accept: "application/json",
        ...(options.body ? { "Content-Type": "application/json" } : {}),
        ...options.headers,
      },
    });
    let data;
    try {
      data = await r.json();
    } catch {
      data = {};
    }
    if (!r.ok) {
      const err = new Error("request failed");
      err.code = data.code;
      err.status = r.status;
      throw err;
    }
    return data;
  }
  function notice(message, error = false) {
    $("page-status").textContent = message;
    $("page-status").className = "notice" + (error ? " error" : "");
  }
  function resetAi() {
    state.aiController?.abort();
    $("ai-results").replaceChildren();
    $("ai-results").classList.add("hidden");
    clear($("ai-status"));
    const box = el("div", "ai-idle");
    box.append(el("div", "idle-line"));
    const p = el("p", "");
    p.append(
      document.createTextNode("숫자만으로 보이지 않는 확인 사항을,"),
      el("br"),
    );
    p.append(el("strong", "", "원본 근거와 함께 살펴보세요."));
    box.append(
      p,
      el("span", "", "AI 의견은 참고 자료이며, 최종 판단은 직접 해 주세요."),
    );
    $("ai-status").append(box);
    $("run-ai").textContent = "AI 검토 시작 ✧";
    $("run-ai").disabled =
      !state.summary || !state.configured || state.loading || state.needsReload;
  }
  function invalidate() {
    state.generation++;
    state.controller?.abort();
    state.originalController?.abort();
    state.aiController?.abort();
    state.date = null;
    state.summary = null;
    state.total = 0;
    state.page = 0;
    state.loading = false;
    state.needsReload = false;
    $("load-day").disabled = false;
    $("load-day").textContent = "하루 조회 →";
    document
      .querySelector(".summary-section")
      .setAttribute("aria-busy", "false");
    $("summary-cards").replaceChildren(
      el(
        "div",
        "summary-placeholder",
        "날짜가 바뀌었습니다. 하루 조회를 눌러 새 내역을 확인하세요.",
      ),
    );
    $("selected-date").textContent = "새 날짜를 조회해 주세요.";
    $("business-count").textContent = "—";
    $("bank-count").textContent = "—";
    $("originals-content").replaceChildren(
      el("p", "empty-small", "선택한 날짜를 조회해 주세요."),
    );
    $("related-uploads").replaceChildren(
      el("p", "empty-small", "하루 조회 후 연결된 업로드를 확인할 수 있어요."),
    );
    $("page-status").classList.add("hidden");
    $("review-status").textContent = "미검토";
    $("review-status").className = "status neutral";
    $("first-use").classList.add("hidden");
    resetAi();
    updatePages();
  }
  function summaryCard(direction, data) {
    const empty = data.comparisonStatus === "NO_DATA";
    const diff = data.comparisonStatus === "TOTAL_DIFF";
    const card = el(
      "article",
      "summary-card " + (direction === "IN" ? "incoming" : "outgoing"),
    );
    const head = el("div", "summary-card-head");
    const name = el("h3", "direction-name");
    name.append(
      el("span", "direction-icon", direction === "IN" ? "↓" : "↑"),
      document.createTextNode(label[direction]),
    );
    const status = el(
      "span",
      "status " + (empty ? "nodata" : diff ? "diff" : "equal"),
      (empty ? "− " : diff ? "! " : "✓ ") +
        (empty ? "자료 없음" : diff ? "금액 차이" : "합계 일치"),
    );
    head.append(name, status);
    card.append(head);
    const sources = {
      NONE: "비교할 자료 없음",
      BUSINESS_ONLY: "업무 자료만 있음",
      BANK_ONLY: "은행 자료만 있음",
      BOTH: "양쪽 자료 있음",
    };
    card.append(
      el(
        "p",
        "source-counts",
        `${sources[data.sourcePresence] || "자료 확인"} · 업무 ${data.businessCount}건 · 은행 ${data.bankCount}건`,
      ),
    );
    const amounts = el("div", "amount-rows");
    for (const [title, value] of [
      ["업무 예정", data.expectedAmount],
      ["은행 실제", data.actualAmount],
    ]) {
      const a = el("div", "");
      a.append(
        el("p", "amount-label", title),
        amountNode(value, "amount-value"),
      );
      amounts.append(a);
    }
    card.append(amounts);
    const difference = el("div", "difference-row");
    difference.append(el("span", "label", empty ? "비교 상태" : "금액 차이"));
    difference.append(
      empty
        ? el("span", "difference-value is-empty", "비교할 자료 없음")
        : amountNode(
            data.difference,
            "difference-value" + (diff ? " is-diff" : ""),
            true,
          ),
    );
    card.append(difference);
    return card;
  }
  function renderSummary(s) {
    $("selected-date").textContent = humanDate(s.date);
    $("summary-cards").replaceChildren(
      summaryCard("IN", s.incoming),
      summaryCard("OUT", s.outgoing),
    );
    $("review-status").textContent = label[s.reviewStatus] || "미검토";
    $("review-status").className =
      "status " +
      (s.reviewStatus === "NEEDS_RECHECK"
        ? "diff"
        : s.reviewStatus === "REVIEWED"
          ? "equal"
          : "neutral");
    $("business-count").textContent = String(
      s.incoming.businessCount + s.outgoing.businessCount,
    );
    $("bank-count").textContent = String(
      s.incoming.bankCount + s.outgoing.bankCount,
    );
    const empty =
      s.incoming.comparisonStatus === "NO_DATA" &&
      s.outgoing.comparisonStatus === "NO_DATA";
    $("first-use").classList.toggle("hidden", !empty);
  }
  async function loadDay(event) {
    event?.preventDefault();
    if (!$("date-form").reportValidity()) return;
    invalidate();
    const date = $("review-date").value;
    state.loading = true;
    const generation = state.generation;
    state.controller = new AbortController();
    const signal = state.controller.signal;
    $("load-day").disabled = true;
    $("load-day").textContent = "조회 중…";
    document
      .querySelector(".summary-section")
      .setAttribute("aria-busy", "true");
    $("summary-cards").replaceChildren(
      el(
        "div",
        "summary-placeholder",
        "선택한 날짜의 금액과 원본을 확인하고 있어요.",
      ),
    );
    try {
      const summary = await request(`/summary?date=${date}`, signal);
      if (generation !== state.generation) return;
      state.date = date;
      state.summary = summary;
      renderSummary(summary);
      const url = new URL(location);
      url.searchParams.set("date", date);
      history.replaceState(null, "", url);
      await Promise.allSettled([
        loadOriginals(),
        loadUploads(generation, signal),
      ]);
      if (generation !== state.generation) return;
      $("page-status").classList.add("hidden");
    } catch (error) {
      if (error.name === "AbortError" || generation !== state.generation)
        return;
      notice(
        "하루 내역을 불러오지 못했습니다. 날짜와 연결 상태를 확인한 뒤 다시 조회해 주세요.",
        true,
      );
      $("summary-cards").replaceChildren(
        el(
          "div",
          "summary-placeholder",
          "아직 결과를 확인하지 못했습니다. 다시 조회해 주세요.",
        ),
      );
    } finally {
      if (generation === state.generation) {
        state.loading = false;
        $("load-day").disabled = false;
        $("load-day").textContent = "하루 조회 →";
        document
          .querySelector(".summary-section")
          .setAttribute("aria-busy", "false");
        resetAi();
        showAvailability();
      }
    }
  }
  function updatePages() {
    $("prev-page").disabled = !state.date || state.page === 0;
    $("next-page").disabled =
      !state.date || (state.page + 1) * state.size >= state.total;
    $("page-number").textContent = String(state.page + 1);
    $("page-description").textContent = state.date
      ? `총 ${state.total.toLocaleString("ko-KR")}건 · ${state.total ? state.page * state.size + 1 : 0}–${Math.min((state.page + 1) * state.size, state.total)}건`
      : "";
  }
  async function loadOriginals() {
    if (!state.date) return;
    state.originalController?.abort();
    state.originalController = new AbortController();
    const signal = state.originalController.signal;
    const generation = state.generation;
    const kind = state.kind;
    const page = state.page;
    const direction = $("direction").value;
    const params = new URLSearchParams({
      from: state.date,
      to: state.date,
      page: String(page),
      size: String(state.size),
    });
    if (direction) params.set("direction", direction);
    $("originals-content").replaceChildren(
      el("p", "empty-small", "원본을 불러오고 있어요."),
    );
    $("prev-page").disabled = true;
    $("next-page").disabled = true;
    try {
      const result = await request(`/originals/${kind}?${params}`, signal);
      if (
        signal.aborted ||
        generation !== state.generation ||
        kind !== state.kind ||
        page !== state.page ||
        direction !== $("direction").value
      )
        return;
      state.total = result.totalElements;
      renderOriginals(result.content, kind);
      updatePages();
    } catch (error) {
      if (error.name === "AbortError" || generation !== state.generation)
        return;
      clear($("originals-content")).append(
        el(
          "p",
          "empty-small",
          "원본을 불러오지 못했습니다. 다시 시도해 주세요.",
        ),
      );
      const retry = el("button", "button secondary", "원본 다시 조회");
      retry.addEventListener("click", () => loadOriginals());
      $("originals-content").append(retry);
    }
  }
  function renderOriginals(rows, kind) {
    const target = clear($("originals-content"));
    if (!rows.length) {
      target.append(
        el(
          "p",
          "empty-small",
          "이 날짜·입출금 조건에 조회된 원본이 없습니다. 파일 등록 여부와는 별개입니다.",
        ),
      );
      return;
    }
    const wrap = el("div", "table-wrap");
    const table = el("table", "");
    const thead = el("thead", "");
    const hr = el("tr", "");
    for (const [text, cls] of [
      [kind === "business" ? "거래처 · 내역" : "거래 내용", ""],
      ["유형", "mobile-hide"],
      ["금액", "num"],
      ["원본 근거", ""],
    ])
      hr.append(el("th", cls, text));
    thead.append(hr);
    table.append(thead);
    const body = el("tbody", "");
    rows.forEach((row) => {
      const tr = el("tr", "");
      tr.id = `original-${kind}-${row.id}`;
      const title = el("td", "");
      title.append(
        el(
          "div",
          "row-title",
          kind === "business"
            ? row.vendorName || row.vendorCode
            : row.counterpartyName || row.description || "은행 거래",
        ),
        el(
          "div",
          "row-subtitle",
          kind === "business"
            ? row.note || row.vendorCode
            : row.description || shortDate(row.bookedDate),
        ),
      );
      tr.append(
        title,
        el("td", "mobile-hide", label[row.eventType || row.direction] || "—"),
        el("td", "num", money(row.amount) + "원"),
      );
      const proof = el("td", "");
      const detail = el("details", "original-details");
      detail.append(el("summary", "", "근거 보기"));
      const dl = el("dl", "");
      for (const [key, value] of [
        ["원본 ID", `${kind === "business" ? "업무" : "은행"} #${row.id}`],
        ["유형", label[row.eventType || row.direction] || "—"],
        ["거래일", shortDate(row.expectedCashDate || row.bookedDate)],
        ["원본 행", `${row.sourceRowNumber}행`],
        ["업로드", `#${row.uploadId}`],
        ["거래 식별값", row.sourceLineId],
        ["기재내용", row.note || row.description || "—"],
      ]) {
        dl.append(el("dt", "", key), el("dd", "", value ?? "—"));
      }
      detail.append(dl);
      proof.append(detail);
      tr.append(proof);
      body.append(tr);
    });
    table.append(body);
    wrap.append(table);
    target.append(wrap);
  }
  async function loadUploads(generation, signal) {
    try {
      const p = new URLSearchParams({
        from: state.date,
        to: state.date,
        page: "0",
        size: "20",
      });
      const result = await request(`/uploads?${p}`, signal);
      if (generation !== state.generation) return;
      const target = clear($("related-uploads"));
      if (!result.content.length) {
        target.append(
          el(
            "p",
            "empty-small",
            "이 날짜의 조회 결과에 연결된 업로드가 없습니다. 전체 등록 이력은 별도로 확인하세요.",
          ),
        );
        return;
      }
      for (const row of result.content) {
        const card = el("article", "upload-card");
        card.id = `upload-${row.uploadId}`;
        card.append(el("div", "file-icon", "CSV"));
        const detail = el("div", "");
        detail.append(
          el("h3", "", row.originalFilename),
          el(
            "p",
            "",
            `${label[row.fileType] || "자료"} · ${row.rowCount}행 · 등록 ${shortDate(row.uploadedAt)}`,
          ),
          el("div", "upload-meta", `업로드 #${row.uploadId}`),
        );
        card.append(detail);
        target.append(card);
      }
      if (result.totalElements > result.content.length)
        target.append(
          el(
            "p",
            "empty-small",
            `연결된 ${result.totalElements}개 파일 중 ${result.content.length}개를 표시합니다. 전체 업로드 이력에서 더 확인하세요.`,
          ),
        );
    } catch (error) {
      if (error.name === "AbortError" || generation !== state.generation)
        return;
      $("related-uploads").replaceChildren(
        el(
          "p",
          "empty-small",
          "관련 업로드 정보를 불러오지 못했습니다. 하루를 다시 조회하거나 전체 이력에서 확인해 주세요.",
        ),
      );
    }
  }
  function showAvailability() {
    if (state.configured || !state.summary) return;
    const box = el("div", "ai-message");
    box.append(
      el("p", "", "현재 AI 검토를 사용할 수 없습니다."),
      el("p", "", "금액과 원본 내역은 계속 확인할 수 있어요."),
    );
    $("ai-status").replaceChildren(box);
    $("run-ai").disabled = true;
  }
  async function capabilities() {
    try {
      const c = await request("/capabilities");
      state.configured = Boolean(c.configured);
    } catch {
      state.configured = false;
    }
    $("run-ai").disabled =
      !state.summary || !state.configured || state.loading || state.needsReload;
    showAvailability();
  }
  function evidenceTitle(item) {
    const data = item.data || {};
    if (item.id.startsWith("summary:"))
      return `${item.id.endsWith("IN") ? "입금" : "출금"} 하루 요약`;
    if (item.id.startsWith("business:"))
      return `업무 #${data.id ?? item.id.split(":")[1]} · ${data.sourceRowNumber ?? "—"}행`;
    if (item.id.startsWith("bank:"))
      return `은행 #${data.id ?? item.id.split(":")[1]} · ${data.sourceRowNumber ?? "—"}행`;
    if (item.id.startsWith("upload:"))
      return `업로드 #${data.uploadId ?? item.id.split(":")[1]}`;
    return item.id;
  }
  function evidenceAnchor(id) {
    return "evidence-" + id.replace(/[^a-zA-Z0-9_-]/g, "-");
  }
  function renderAi(result) {
    const target = clear($("ai-results"));
    target.classList.remove("hidden");
    const head = el("div", "ai-result-head");
    head.append(
      el("h3", "", "검토 의견이 준비되었습니다"),
      el(
        "span",
        "",
        `${humanDate(result.date)} 기준 · ${result.evidence.length}개 근거 확인`,
      ),
    );
    target.append(head);
    const lookup = new Map(result.evidence.map((x) => [x.id, x]));
    const grid = el("div", "claim-grid");
    for (const [title, key, badge] of [
      ["확인된 사실", "facts", "조회 근거"],
      ["가능한 원인", "possibleCauses", "추정"],
      ["추가 확인 사항", "checks", "직접 확인"],
    ]) {
      const section = el("section", "claim-section");
      const h = el("h3", "", title);
      h.append(el("span", "", badge));
      section.append(h);
      const ul = el("ul", "");
      const claims = result[key] || [];
      if (!claims.length)
        ul.append(el("li", "", "추가로 정리된 내용이 없습니다."));
      for (const claim of claims) {
        const li = el("li", "");
        li.append(el("p", "", claim.text));
        for (const id of claim.evidenceIds || []) {
          const evidence = lookup.get(id);
          if (!evidence) continue;
          const b = el("button", "evidence-link", evidenceTitle(evidence));
          b.type = "button";
          b.addEventListener("click", () => {
            const source = $(evidenceAnchor(id));
            if (!source) return;
            source.open = true;
            source.scrollIntoView({
              behavior: matchMedia("(prefers-reduced-motion: reduce)").matches
                ? "auto"
                : "smooth",
              block: "center",
            });
            source.querySelector("summary").focus({ preventScroll: true });
            source.classList.remove("flash");
            requestAnimationFrame(() => source.classList.add("flash"));
          });
          li.append(b);
        }
        ul.append(li);
      }
      section.append(ul);
      grid.append(section);
    }
    target.append(grid);
    const section = el("section", "evidence-section");
    section.append(
      el("h3", "", "검토 시점에 조회한 근거"),
      el(
        "p",
        "evidence-context",
        "AI 의견은 이 근거를 조회한 시점을 기준으로 합니다. 아래 원본 목록은 다시 조회한 내역이므로 이후 변경 사항이 반영될 수 있습니다.",
      ),
    );
    const list = el("div", "evidence-list");
    for (const item of result.evidence) {
      const card = el("details", "evidence-card");
      card.id = evidenceAnchor(item.id);
      card.append(el("summary", "", evidenceTitle(item)));
      const d = item.data || {};
      if (item.id.startsWith("summary:")) {
        for (const [key, val] of [
          ["업무 예정", d.expectedAmount],
          ["은행 실제", d.actualAmount],
          ["금액 차이", d.difference],
        ])
          if (val !== undefined)
            card.append(el("p", "", `${key} ${money(val)}원`));
        card.append(
          el("p", "", "자료 존재와 금액 상태는 위 하루 요약에서 확인하세요."),
        );
      } else if (item.id.startsWith("upload:")) {
        card.append(
          el("p", "", d.originalFilename || "업로드 파일"),
          el(
            "p",
            "",
            `${label[d.fileType] || "자료"} · ${d.rowCount ?? "—"}행 · 등록 ${shortDate(d.uploadedAt)}`,
          ),
        );
      } else {
        card.append(
          el(
            "p",
            "",
            `${label[d.eventType || d.direction] || "거래"} · ${money(d.amount)}원`,
          ),
          el(
            "p",
            "",
            `거래일 ${shortDate(d.expectedCashDate || d.bookedDate)} · 업로드 #${d.uploadId ?? "—"} · 원본 ${d.sourceRowNumber ?? "—"}행`,
          ),
          el("p", "", `거래 식별값: ${d.sourceLineId || "—"}`),
        );
        if (d.note || d.description)
          card.append(el("p", "", d.note || d.description));
      }
      list.append(card);
    }
    section.append(list);
    target.append(section);
    const limits = [
      "개별 거래 매칭은 평가하지 않았습니다. AI 의견을 확인해 직접 검토해 주세요.",
      ...(result.limitations || []),
    ];
    target.append(el("p", "limitation", limits.join(" ")));
  }
  async function runAi() {
    if (!state.summary || !state.configured || state.loading || state.needsReload)
      return;
    const generation = state.generation;
    const date = state.date;
    state.aiController?.abort();
    state.aiController = new AbortController();
    const signal = state.aiController.signal;
    $("run-ai").disabled = true;
    $("run-ai").textContent = "검토 중…";
    $("ai-results").classList.add("hidden");
    const progress = el("div", "ai-message");
    progress.append(
      el("span", "spinner"),
      el("p", "", "원본 근거를 확인하고 있어요"),
      el("p", "", "날짜를 바꾸면 이전 결과는 표시하지 않습니다."),
      el("p", "", "이미 요청한 검토는 계속 처리될 수 있습니다."),
    );
    $("ai-status").replaceChildren(progress);
    try {
      const result = await request("/reviews", signal, {
        method: "POST",
        body: JSON.stringify({ date }),
      });
      if (generation !== state.generation || result.date !== state.date) return;
      // The model may have started after a newer upload than the page's first
      // summary. Keep the visible amounts tied to the returned Java summary.
      if (
        !result.summary ||
        result.summary.date !== date ||
        !result.summary.incoming ||
        !result.summary.outgoing
      ) {
        throw new Error("invalid review summary");
      }
      state.summary = result.summary;
      renderSummary(result.summary);
      state.page = 0;
      await Promise.allSettled([
        loadOriginals(),
        loadUploads(generation, signal),
      ]);
      if (generation !== state.generation || signal.aborted) return;
      clear($("ai-status")).append(
        el("p", "visually-hidden", "검토 의견이 준비되었습니다."),
      );
      renderAi(result);
      $("ai-results").focus({ preventScroll: true });
      $("run-ai").textContent = "다시 검토 ✧";
    } catch (error) {
      if (error.name === "AbortError" || generation !== state.generation)
        return;
      if (error.code === "AI_DATA_CHANGED") {
        state.needsReload = true;
        notice(
          "검토 중 자료가 바뀌었습니다. 하루 내역을 다시 조회해 최신 금액과 원본을 확인해 주세요.",
          true,
        );
        const box = el("div", "ai-message error");
        box.append(
          el("p", "", "자료가 바뀌어 검토 의견을 표시하지 않았습니다."),
        );
        const reload = el("button", "button secondary", "하루 다시 조회");
        reload.id = "reload-day-from-ai";
        reload.type = "button";
        reload.addEventListener("click", () => loadDay());
        box.append(reload);
        $("ai-status").replaceChildren(box);
        $("run-ai").textContent = "하루 조회 후 검토 가능";
        return;
      }
      const message =
        error.code === "AI_NOT_CONFIGURED"
          ? "현재 AI 검토를 사용할 수 없습니다."
          : error.status === 504
            ? "검토 시간이 길어져 중단했습니다."
            : error.status === 429
              ? "요청이 많습니다. 잠시 후 다시 시도해 주세요."
              : "검토 의견을 준비하지 못했습니다.";
      const box = el("div", "ai-message error");
      box.append(
        el("p", "", message),
        el(
          "p",
          "",
          "금액과 원본은 그대로 확인할 수 있습니다. 잠시 후 다시 시도해 주세요.",
        ),
      );
      $("ai-status").replaceChildren(box);
      $("run-ai").textContent = "AI 검토 다시 시도";
    } finally {
      if (generation === state.generation)
        $("run-ai").disabled =
          !state.summary || !state.configured || state.loading || state.needsReload;
    }
  }
  $("date-form").addEventListener("submit", loadDay);
  $("review-date").addEventListener("change", invalidate);
  $("direction").addEventListener("change", () => {
    state.page = 0;
    loadOriginals();
  });
  $("run-ai").addEventListener("click", runAi);
  $("prev-page").addEventListener("click", () => {
    if (state.page > 0) {
      state.page--;
      loadOriginals();
    }
  });
  $("next-page").addEventListener("click", () => {
    if ((state.page + 1) * state.size < state.total) {
      state.page++;
      loadOriginals();
    }
  });
  const tabs = [...document.querySelectorAll("[data-kind]")];
  function selectTab(tab) {
    state.kind = tab.dataset.kind;
    state.page = 0;
    tabs.forEach((t) => {
      const active = t === tab;
      t.classList.toggle("selected", active);
      t.setAttribute("aria-selected", String(active));
      t.tabIndex = active ? 0 : -1;
    });
    $("originals-content").setAttribute("aria-labelledby", tab.id);
    loadOriginals();
  }
  tabs.forEach((tab, i) => {
    tab.addEventListener("click", () => selectTab(tab));
    tab.addEventListener("keydown", (event) => {
      if (["ArrowLeft", "ArrowRight", "Home", "End"].includes(event.key)) {
        event.preventDefault();
        const next =
          event.key === "Home"
            ? 0
            : event.key === "End"
              ? tabs.length - 1
              : (i + (event.key === "ArrowRight" ? 1 : -1) + tabs.length) %
                tabs.length;
        tabs[next].focus();
        selectTab(tabs[next]);
      }
    });
  });
  const requested = new URLSearchParams(location.search).get("date");
  $("review-date").value = /^\d{4}-\d{2}-\d{2}$/.test(requested || "")
    ? requested
    : today();
  capabilities();
  loadDay();
})();
