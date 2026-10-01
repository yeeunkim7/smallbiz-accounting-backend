(function () {
	"use strict";

	var PAGE_SIZE = 20;
	var loadSeq = 0;
	var dayState = null;
	var saving = false;
	var vendorPage = 0;
	var vendorEditId = null;
	var vendorSaving = false;
	var uploadsPage = 0;
	var uploadsFilter = "";
	var selectedUploadId = null;
	var uploading = false;
	var reconEpoch = 0;

	function $(id) {
		return document.getElementById(id);
	}

	function text(el, value) {
		el.textContent = value == null ? "" : String(value);
	}

	function td(value, className) {
		var cell = document.createElement("td");
		if (className) {
			cell.className = className;
		}
		cell.textContent = value == null || value === "" ? "—" : String(value);
		return cell;
	}

	function money(n) {
		if (n == null || n === "") {
			return "—";
		}
		return new Intl.NumberFormat("ko-KR").format(Number(n)) + "원";
	}

	function formatInstant(value) {
		if (!value) {
			return "—";
		}
		var d = new Date(value);
		if (Number.isNaN(d.getTime())) {
			return String(value);
		}
		return d.toLocaleString("ko-KR");
	}

	function totalLabel(status) {
		if (status === "TOTAL_EQUAL") {
			return { text: "금액 일치", cls: "ok" };
		}
		if (status === "TOTAL_DIFF") {
			return { text: "금액 다름", cls: "diff" };
		}
		return { text: status || "—", cls: "info" };
	}

	function reviewLabel(status) {
		if (status === "UNREVIEWED") {
			return { text: "미검토", cls: "info" };
		}
		if (status === "REVIEWED") {
			return { text: "검토 완료", cls: "ok" };
		}
		if (status === "NEEDS_RECHECK") {
			return { text: "재확인 필요", cls: "warn" };
		}
		return { text: status || "—", cls: "info" };
	}

	function eventTypeLabel(type) {
		if (type === "CHARGE_EXPECTED") {
			return "충전 예정";
		}
		if (type === "SETTLEMENT_EXPECTED") {
			return "정산 예정";
		}
		if (type === "REFUND_EXPECTED") {
			return "환불 예정";
		}
		if (type === "USAGE") {
			return "이용";
		}
		return type || "—";
	}

	function directionLabel(dir) {
		if (dir === "IN") {
			return "입금";
		}
		if (dir === "OUT") {
			return "출금";
		}
		return dir || "—";
	}

	function badge(info) {
		var span = document.createElement("span");
		span.className = "badge " + info.cls;
		span.textContent = info.text;
		return span;
	}

	function showBanner(el, kind, message) {
		el.className = "banner " + kind;
		el.classList.remove("hidden");
		text(el, message);
	}

	function hideBanner(el) {
		el.classList.add("hidden");
		text(el, "");
	}

	function parseHash() {
		var raw = (location.hash || "#/daily").replace(/^#/, "");
		var parts = raw.split("/").filter(Boolean);
		if (parts[0] === "monthly") {
			return { view: "monthly" };
		}
		if (parts[0] === "day" && parts[1]) {
			return { view: "day", date: parts[1] };
		}
		if (parts[0] === "upload") {
			return { view: "upload" };
		}
		if (parts[0] === "uploads") {
			return { view: "uploads" };
		}
		if (parts[0] === "vendors") {
			return { view: "vendors" };
		}
		return { view: "daily" };
	}

	function setNav(view) {
		document.querySelectorAll("nav a").forEach(function (a) {
			var target = a.getAttribute("data-view");
			a.classList.toggle("active", target === view || (view === "day" && target === "daily"));
		});
		$("view-daily").classList.toggle("hidden", view !== "daily");
		$("view-monthly").classList.toggle("hidden", view !== "monthly");
		$("view-day").classList.toggle("hidden", view !== "day");
		$("view-upload").classList.toggle("hidden", view !== "upload");
		$("view-uploads").classList.toggle("hidden", view !== "uploads");
		$("view-vendors").classList.toggle("hidden", view !== "vendors");
	}

	async function apiGet(path) {
		var res;
		try {
			res = await fetch(path, { headers: { Accept: "application/json" } });
		} catch (e) {
			var err = new Error("서버에 연결할 수 없습니다.");
			err.kind = "network";
			throw err;
		}
		var body = null;
		try {
			body = await res.json();
		} catch (e) {
			body = null;
		}
		if (!res.ok) {
			var msg = (body && body.message) ? body.message : "요청을 처리하지 못했습니다. (HTTP " + res.status + ")";
			var httpErr = new Error(msg);
			httpErr.kind = "http";
			httpErr.status = res.status;
			httpErr.body = body;
			throw httpErr;
		}
		return body;
	}

	async function apiPut(path, payload) {
		var res;
		try {
			res = await fetch(path, {
				method: "PUT",
				headers: { "Content-Type": "application/json", Accept: "application/json" },
				body: JSON.stringify(payload)
			});
		} catch (e) {
			var err = new Error("서버에 연결할 수 없습니다.");
			err.kind = "network";
			throw err;
		}
		var body = null;
		try {
			body = await res.json();
		} catch (e) {
			body = null;
		}
		return { status: res.status, body: body };
	}

	async function apiSendJson(method, path, payload) {
		var res;
		try {
			res = await fetch(path, {
				method: method,
				headers: { "Content-Type": "application/json", Accept: "application/json" },
				body: JSON.stringify(payload)
			});
		} catch (e) {
			var err = new Error("서버에 연결할 수 없습니다.");
			err.kind = "network";
			throw err;
		}
		var body = null;
		try {
			body = await res.json();
		} catch (e) {
			body = null;
		}
		return { status: res.status, body: body };
	}

	async function apiPostFile(path, formData) {
		var res;
		try {
			res = await fetch(path, {
				method: "POST",
				headers: { Accept: "application/json" },
				body: formData
			});
		} catch (e) {
			var err = new Error("서버에 연결할 수 없습니다.");
			err.kind = "network";
			throw err;
		}
		var body = null;
		try {
			body = await res.json();
		} catch (e) {
			body = null;
		}
		return { status: res.status, body: body };
	}

	function fillErrorList(el, items, truncated) {
		el.replaceChildren();
		(items || []).forEach(function (item) {
			var li = document.createElement("li");
			li.textContent = window.ReconUploadClient.formatFieldError(item);
			el.appendChild(li);
		});
		if (truncated) {
			var more = document.createElement("li");
			more.textContent = "오류가 더 있어 일부만 표시합니다.";
			el.appendChild(more);
		}
	}

	function noteOriginalsMayHaveChanged() {
		reconEpoch += 1;
		if (dayState) {
			dayState.stale = true;
		}
	}

	function monthValue() {
		var v = $("month-input").value;
		return v || "2026-09";
	}

	async function loadDaily() {
		var seq = ++loadSeq;
		var from = $("daily-from").value;
		var to = $("daily-to").value;
		var banner = $("daily-banner");
		var tbody = $("daily-body");
		tbody.replaceChildren();
		showBanner(banner, "loading", "불러오는 중…");
		try {
			var data = await apiGet("/api/v1/reconciliations/daily?from=" + encodeURIComponent(from)
				+ "&to=" + encodeURIComponent(to));
			if (seq !== loadSeq) {
				return;
			}
			var days = (data && data.days) ? data.days : [];
			if (days.length === 0) {
				showBanner(banner, "empty", "조회된 날짜가 없습니다.");
				return;
			}
			hideBanner(banner);
			days.forEach(function (day) {
				var tr = document.createElement("tr");
				tr.className = "clickable";
				tr.addEventListener("click", function () {
					location.hash = "#/day/" + day.date;
				});
				tr.appendChild(td(day.date));
				tr.appendChild(td(money(day.expectedInAmount), "num"));
				tr.appendChild(td(money(day.actualInAmount), "num"));
				tr.appendChild(td(money(day.inDifference), "num"));
				var inCell = document.createElement("td");
				inCell.appendChild(badge(totalLabel(day.inTotalStatus)));
				tr.appendChild(inCell);
				tr.appendChild(td(money(day.expectedOutAmount), "num"));
				tr.appendChild(td(money(day.actualOutAmount), "num"));
				tr.appendChild(td(money(day.outDifference), "num"));
				var outCell = document.createElement("td");
				outCell.appendChild(badge(totalLabel(day.outTotalStatus)));
				tr.appendChild(outCell);
				var revCell = document.createElement("td");
				revCell.appendChild(badge(reviewLabel(day.reviewStatus)));
				tr.appendChild(revCell);
				tbody.appendChild(tr);
			});
		} catch (e) {
			if (seq !== loadSeq) {
				return;
			}
			showBanner(banner, "error", e.kind === "network" ? e.message : "조회에 실패했습니다. " + e.message);
		}
	}

	async function loadMonthly() {
		var seq = ++loadSeq;
		var ym = monthValue();
		var banner = $("monthly-banner");
		showBanner(banner, "loading", "불러오는 중…");
		try {
			var data = await apiGet("/api/v1/reconciliations/monthly?yearMonth=" + encodeURIComponent(ym));
			if (seq !== loadSeq) {
				return;
			}
			hideBanner(banner);
			text($("m-expected-in"), money(data.expectedInAmount));
			text($("m-actual-in"), money(data.actualInAmount));
			text($("m-diff-in"), money(data.inDifference));
			$("m-in-status").replaceChildren(badge(totalLabel(data.inTotalStatus)));
			text($("m-expected-out"), money(data.expectedOutAmount));
			text($("m-actual-out"), money(data.actualOutAmount));
			text($("m-diff-out"), money(data.outDifference));
			$("m-out-status").replaceChildren(badge(totalLabel(data.outTotalStatus)));
			text($("m-data-days"), String(data.dataDayCount) + "일");
			text($("m-diff-days"), String(data.differenceDayCount) + "일");
			if (Number(data.dataDayCount) === 0) {
				showBanner(banner, "empty", "해당 월에 대사 데이터가 없습니다.");
			}
		} catch (e) {
			if (seq !== loadSeq) {
				return;
			}
			showBanner(banner, "error", e.kind === "network" ? e.message : "조회에 실패했습니다. " + e.message);
		}
	}

	function renderPager(el, page, size, total, onPrev, onNext) {
		el.replaceChildren();
		var pages = Math.max(1, Math.ceil(total / size) || 1);
		var info = document.createElement("span");
		info.textContent = "총 " + total + "건 · " + (page + 1) + " / " + pages + "쪽";
		var prev = document.createElement("button");
		prev.type = "button";
		prev.textContent = "이전";
		prev.disabled = page <= 0;
		prev.addEventListener("click", onPrev);
		var next = document.createElement("button");
		next.type = "button";
		next.textContent = "다음";
		next.disabled = (page + 1) * size >= total;
		next.addEventListener("click", onNext);
		el.appendChild(prev);
		el.appendChild(next);
		el.appendChild(info);
	}

	async function fetchUploadMeta(ids) {
		var map = {};
		var unique = Array.from(new Set(ids.filter(Boolean)));
		for (var i = 0; i < unique.length; i++) {
			try {
				map[unique[i]] = await apiGet("/api/v1/uploads/" + unique[i]);
			} catch (e) {
				map[unique[i]] = null;
			}
		}
		return map;
	}

	function fillBusinessTable(rows, uploads) {
		var tbody = $("biz-body");
		tbody.replaceChildren();
		if (!rows.length) {
			return;
		}
		rows.forEach(function (row) {
			var tr = document.createElement("tr");
			tr.appendChild(td(row.vendorCode));
			tr.appendChild(td(row.vendorName));
			tr.appendChild(td(eventTypeLabel(row.eventType)));
			tr.appendChild(td(money(row.amount), "num"));
			tr.appendChild(td(row.note, "clip"));
			tr.appendChild(td(row.sourceLineId, "clip"));
			tr.appendChild(td(String(row.sourceRowNumber), "num"));
			var meta = uploads[row.uploadId];
			tr.appendChild(td(meta ? meta.originalFilename : "—", "clip"));
			tr.appendChild(td(meta ? formatInstant(meta.uploadedAt) : "—"));
			tbody.appendChild(tr);
		});
	}

	function fillBankTable(rows, uploads) {
		var tbody = $("bank-body");
		tbody.replaceChildren();
		if (!rows.length) {
			return;
		}
		rows.forEach(function (row) {
			var tr = document.createElement("tr");
			tr.appendChild(td(directionLabel(row.direction)));
			tr.appendChild(td(money(row.amount), "num"));
			tr.appendChild(td(row.counterpartyName, "clip"));
			tr.appendChild(td(row.description, "clip"));
			tr.appendChild(td(row.sourceLineId, "clip"));
			tr.appendChild(td(String(row.sourceRowNumber), "num"));
			var meta = uploads[row.uploadId];
			tr.appendChild(td(meta ? meta.originalFilename : "—", "clip"));
			tr.appendChild(td(meta ? formatInstant(meta.uploadedAt) : "—"));
			tbody.appendChild(tr);
		});
	}

	async function loadOriginalPage(kind) {
		if (!dayState || dayState.stale) {
			return;
		}
		var seq = dayState.seq;
		var date = dayState.date;
		var page = kind === "biz" ? dayState.bizPage : dayState.bankPage;
		var path = kind === "biz"
			? "/api/v1/reconciliations/daily/" + date + "/business?page=" + page + "&size=" + PAGE_SIZE
			: "/api/v1/reconciliations/daily/" + date + "/bank?page=" + page + "&size=" + PAGE_SIZE;
		var data = await apiGet(path);
		if (seq !== loadSeq) {
			return;
		}
		var ids = (data.content || []).map(function (r) { return r.uploadId; });
		var uploads = await fetchUploadMeta(ids);
		if (seq !== loadSeq) {
			return;
		}
		if (kind === "biz") {
			dayState.bizTotal = data.totalElements;
			fillBusinessTable(data.content || [], uploads);
			renderPager($("biz-pager"), data.page, data.size, data.totalElements, function () {
				dayState.bizPage = Math.max(0, dayState.bizPage - 1);
				safeOriginal("biz");
			}, function () {
				dayState.bizPage += 1;
				safeOriginal("biz");
			});
		} else {
			dayState.bankTotal = data.totalElements;
			fillBankTable(data.content || [], uploads);
			renderPager($("bank-pager"), data.page, data.size, data.totalElements, function () {
				dayState.bankPage = Math.max(0, dayState.bankPage - 1);
				safeOriginal("bank");
			}, function () {
				dayState.bankPage += 1;
				safeOriginal("bank");
			});
		}
	}

	function safeOriginal(kind) {
		loadOriginalPage(kind).catch(function (e) {
			showBanner($("day-banner"), "error", e.message);
		});
	}

	function applyReview(review, preserveMemo) {
		var status = review.status || "UNREVIEWED";
		$("review-status").replaceChildren(badge(reviewLabel(status)));
		text($("review-last"), formatInstant(review.lastReviewedAt));
		if (!preserveMemo) {
			$("review-memo").value = review.memo || "";
		}
		var select = $("review-status-input");
		if (status === "REVIEWED") {
			select.value = "REVIEWED";
		} else {
			select.value = "UNREVIEWED";
		}
	}

	function setSaveEnabled(enabled) {
		$("btn-save").disabled = !enabled || saving;
		$("review-status-input").disabled = !enabled || saving;
		$("review-memo").disabled = saving;
	}

	function markStale(message) {
		if (!dayState) {
			return;
		}
		dayState.stale = true;
		showBanner($("day-banner"), "warn", message);
		setSaveEnabled(false);
	}

	async function loadDay(date) {
		var seq = ++loadSeq;
		var keepMemo = dayState && dayState.date === date;
		var previousMemo = keepMemo ? $("review-memo").value : null;
		dayState = {
			seq: seq,
			date: date,
			version: null,
			stale: false,
			bizPage: 0,
			bankPage: 0
		};
		saving = false;
		text($("day-title"), date + " 상세");
		$("biz-body").replaceChildren();
		$("bank-body").replaceChildren();
		$("biz-pager").replaceChildren();
		$("bank-pager").replaceChildren();
		setSaveEnabled(false);
		showBanner($("day-banner"), "loading", "불러오는 중…");
		try {
			var reviewBefore = await apiGet("/api/v1/reconciliations/daily/" + date + "/review");
			if (seq !== loadSeq) {
				return;
			}
			var daily = await apiGet("/api/v1/reconciliations/daily?from=" + date + "&to=" + date);
			if (seq !== loadSeq) {
				return;
			}
			var days = (daily && daily.days) ? daily.days : [];
			var summary = days[0] || null;
			var cardsIn = $("day-cards-in");
			var cardsOut = $("day-cards-out");
			cardsIn.replaceChildren();
			cardsOut.replaceChildren();
			function addCard(target, k, vNode) {
				var div = document.createElement("div");
				div.className = "card";
				var kk = document.createElement("div");
				kk.className = "k";
				kk.textContent = k;
				var vv = document.createElement("div");
				vv.className = "v";
				if (typeof vNode === "string") {
					vv.textContent = vNode;
				} else {
					vv.appendChild(vNode);
				}
				div.appendChild(kk);
				div.appendChild(vv);
				target.appendChild(div);
			}
			if (summary) {
				addCard(cardsIn, "예정 입금", money(summary.expectedInAmount));
				addCard(cardsIn, "실제 입금", money(summary.actualInAmount));
				addCard(cardsIn, "입금 차이", money(summary.inDifference));
				addCard(cardsIn, "입금 합계", badge(totalLabel(summary.inTotalStatus)));
				addCard(cardsOut, "예정 출금", money(summary.expectedOutAmount));
				addCard(cardsOut, "실제 출금", money(summary.actualOutAmount));
				addCard(cardsOut, "출금 차이", money(summary.outDifference));
				addCard(cardsOut, "출금 합계", badge(totalLabel(summary.outTotalStatus)));
			} else {
				addCard(cardsIn, "집계", "이 날짜의 현금 대사 데이터가 없습니다.");
			}
			await loadOriginalPage("biz");
			if (seq !== loadSeq) {
				return;
			}
			await loadOriginalPage("bank");
			if (seq !== loadSeq) {
				return;
			}
			var reviewAfter = await apiGet("/api/v1/reconciliations/daily/" + date + "/review");
			if (seq !== loadSeq) {
				return;
			}
			if (window.ReconReviewSave.versionsChanged(reviewBefore.version, reviewAfter.version)) {
				markStale("조회 중 원본이 바뀌었습니다. 다시 조회한 뒤 저장하세요.");
				applyReview(reviewAfter, true);
				if (keepMemo) {
					$("review-memo").value = previousMemo;
				}
				return;
			}
			dayState.version = reviewAfter.version;
			applyReview(reviewAfter, keepMemo);
			if (keepMemo) {
				$("review-memo").value = previousMemo;
			}
			hideBanner($("day-banner"));
			if (!summary) {
				showBanner($("day-banner"), "empty", "이 날짜에는 집계할 현금 내역이 없습니다. 원본이 비어 있을 수 있습니다.");
			}
			setSaveEnabled(true);
		} catch (e) {
			if (seq !== loadSeq) {
				return;
			}
			showBanner($("day-banner"), "error", e.kind === "network" ? e.message : "조회에 실패했습니다. " + e.message);
			setSaveEnabled(false);
		}
	}

	async function saveReview() {
		if (!dayState || dayState.stale || saving) {
			return;
		}
		var status = $("review-status-input").value;
		if (!window.ReconReviewSave.allowedPutStatus(status)) {
			showBanner($("day-banner"), "error", "미검토 또는 검토 완료만 저장할 수 있습니다.");
			return;
		}
		saving = true;
		setSaveEnabled(false);
		showBanner($("day-banner"), "loading", "저장 중…");
		var memo = $("review-memo").value;
		try {
			var payload = window.ReconReviewSave.buildPutPayload(status, memo, dayState.version);
			var result = await apiPut("/api/v1/reconciliations/daily/" + dayState.date + "/review", payload);
			var interpreted = window.ReconReviewSave.interpretPutHttp(result.status, result.body);
			$("review-memo").value = memo;
			if (interpreted.conflict) {
				markStale(interpreted.message);
				return;
			}
			if (!interpreted.ok) {
				showBanner($("day-banner"), "error", interpreted.message);
				setSaveEnabled(true);
				return;
			}
			var date = dayState.date;
			await loadDay(date);
			if (dayState && !dayState.stale && dayState.date === date) {
				showBanner($("day-banner"), "ok", "저장했습니다.");
			}
		} catch (e) {
			showBanner($("day-banner"), "error", e.message);
			setSaveEnabled(true);
		} finally {
			saving = false;
			if (dayState && !dayState.stale) {
				setSaveEnabled(true);
			}
		}
	}

	function selectedUploadKind() {
		var checked = document.querySelector("input[name='upload-kind']:checked");
		return checked ? checked.value : "";
	}

	function setVendorFormMode(editId, code) {
		vendorEditId = editId;
		var codeInput = $("vendor-code");
		if (editId) {
			text($("vendor-form-title"), "거래처 수정");
			text($("btn-vendor-save"), "수정 저장");
			codeInput.readOnly = true;
			codeInput.value = code || codeInput.value;
		} else {
			text($("vendor-form-title"), "거래처 등록");
			text($("btn-vendor-save"), "등록");
			codeInput.readOnly = false;
		}
	}

	function setVendorSaving(on) {
		vendorSaving = on;
		$("btn-vendor-save").disabled = on;
		$("btn-vendor-cancel").disabled = on;
		$("vendor-name").disabled = on;
		$("vendor-settlement").disabled = on;
		$("vendor-code").disabled = on;
	}

	async function loadVendors() {
		var seq = ++loadSeq;
		var banner = $("vendors-banner");
		var tbody = $("vendors-body");
		tbody.replaceChildren();
		showBanner(banner, "loading", "불러오는 중…");
		try {
			var data = await apiGet("/api/v1/vendors?page=" + vendorPage + "&size=" + PAGE_SIZE);
			if (seq !== loadSeq) {
				return;
			}
			var rows = (data && data.content) ? data.content : [];
			if (rows.length === 0) {
				showBanner(banner, "empty", "등록된 거래처가 없습니다.");
			} else {
				hideBanner(banner);
			}
			rows.forEach(function (row) {
				var tr = document.createElement("tr");
				tr.className = "clickable";
				if (vendorEditId === row.id) {
					tr.classList.add("selected");
				}
				tr.tabIndex = 0;
				tr.addEventListener("click", function () {
					openVendorEdit(row);
				});
				tr.addEventListener("keydown", function (ev) {
					if (ev.key === "Enter" || ev.key === " ") {
						ev.preventDefault();
						openVendorEdit(row);
					}
				});
				tr.appendChild(td(row.vendorCode));
				tr.appendChild(td(row.vendorName));
				tr.appendChild(td(window.ReconVendorForms.settlementLabel(row.settlementType)));
				tbody.appendChild(tr);
			});
			renderPager($("vendors-pager"), data.page, data.size, data.totalElements, function () {
				vendorPage = Math.max(0, vendorPage - 1);
				loadVendors();
			}, function () {
				vendorPage += 1;
				loadVendors();
			});
		} catch (e) {
			if (seq !== loadSeq) {
				return;
			}
			showBanner(banner, "error", e.kind === "network" ? e.message : "조회에 실패했습니다. " + e.message);
		}
	}

	function openVendorEdit(row) {
		setVendorFormMode(row.id, row.vendorCode);
		$("vendor-code").value = row.vendorCode;
		$("vendor-name").value = row.vendorName;
		$("vendor-settlement").value = row.settlementType;
		fillErrorList($("vendor-errors"), [], false);
		hideBanner($("vendors-banner"));
		loadVendorDetail(row.id);
		document.querySelectorAll("#vendors-body tr").forEach(function (tr) {
			tr.classList.toggle("selected", tr.children[0] && tr.children[0].textContent === row.vendorCode);
		});
	}

	async function loadVendorDetail(id) {
		try {
			var row = await apiGet("/api/v1/vendors/" + id);
			if (vendorEditId !== id) {
				return;
			}
			$("vendor-code").value = row.vendorCode;
			$("vendor-name").value = row.vendorName;
			$("vendor-settlement").value = row.settlementType;
		} catch (e) {
			showBanner($("vendors-banner"), "error", e.message);
		}
	}

	function resetVendorCreate() {
		setVendorFormMode(null, "");
		$("vendor-code").value = "";
		$("vendor-name").value = "";
		$("vendor-settlement").value = "PREPAID";
		fillErrorList($("vendor-errors"), [], false);
		document.querySelectorAll("#vendors-body tr").forEach(function (tr) {
			tr.classList.remove("selected");
		});
	}

	async function saveVendor(ev) {
		if (ev) {
			ev.preventDefault();
		}
		if (vendorSaving) {
			return;
		}
		var code = $("vendor-code").value;
		var name = $("vendor-name").value;
		var settlement = $("vendor-settlement").value;
		var V = window.ReconVendorForms;
		var conv = vendorEditId
			? V.convenienceUpdateCheck(name, settlement)
			: V.convenienceCreateCheck(code, name, settlement);
		if (!conv.ok) {
			showBanner($("vendors-banner"), "error", conv.message);
			fillErrorList($("vendor-errors"), conv.fieldErrors, false);
			return;
		}
		setVendorSaving(true);
		showBanner($("vendors-banner"), "loading", "저장 중…");
		fillErrorList($("vendor-errors"), [], false);
		try {
			var result;
			if (vendorEditId) {
				var updatePayload = V.buildUpdatePayload(name, settlement);
				result = await apiSendJson("PUT", "/api/v1/vendors/" + vendorEditId, updatePayload);
			} else {
				var createPayload = V.buildCreatePayload(code, name, settlement);
				result = await apiSendJson("POST", "/api/v1/vendors", createPayload);
			}
			$("vendor-code").value = code;
			$("vendor-name").value = name;
			$("vendor-settlement").value = settlement;
			var interpreted = V.interpretVendorHttp(result.status, result.body);
			if (!interpreted.ok) {
				showBanner($("vendors-banner"), "error", interpreted.message);
				fillErrorList($("vendor-errors"), interpreted.fieldErrors, false);
				return;
			}
			fillErrorList($("vendor-errors"), [], false);
			if (!vendorEditId && interpreted.vendor && interpreted.vendor.id) {
				setVendorFormMode(interpreted.vendor.id, interpreted.vendor.vendorCode || code);
			}
			await loadVendors();
			showBanner($("vendors-banner"), "ok", vendorEditId ? "수정했습니다." : "등록했습니다.");
		} catch (e) {
			$("vendor-code").value = code;
			$("vendor-name").value = name;
			$("vendor-settlement").value = settlement;
			showBanner($("vendors-banner"), "error", e.message);
		} finally {
			setVendorSaving(false);
			$("vendor-code").readOnly = !!vendorEditId;
		}
	}

	function setUploading(on) {
		uploading = on;
		$("btn-upload").disabled = on;
		$("upload-file").disabled = on;
		document.querySelectorAll("input[name='upload-kind']").forEach(function (el) {
			el.disabled = on;
		});
	}

	function renderUploadResult(upload) {
		var box = $("upload-result");
		box.classList.remove("hidden");
		box.replaceChildren();
		function line(label, value) {
			var p = document.createElement("p");
			p.textContent = label + ": " + (value == null || value === "" ? "—" : String(value));
			box.appendChild(p);
		}
		line("파일명", upload.originalFilename);
		line("저장 행 수", upload.rowCount);
		line("업로드 시각", formatInstant(upload.uploadedAt));
		if (upload.uploadId != null) {
			line("업로드 번호", upload.uploadId);
		}
	}

	async function submitUpload(ev) {
		if (ev) {
			ev.preventDefault();
		}
		if (uploading) {
			return;
		}
		var U = window.ReconUploadClient;
		var kind = selectedUploadKind();
		var fileInput = $("upload-file");
		var file = fileInput.files && fileInput.files[0] ? fileInput.files[0] : null;
		var conv = U.convenienceUploadCheck(kind, file);
		if (!conv.ok) {
			showBanner($("upload-banner"), "error", conv.message);
			fillErrorList($("upload-errors"), conv.fieldErrors, false);
			$("upload-result").classList.add("hidden");
			return;
		}
		var sizeNote = U.convenienceSizeNote(file);
		setUploading(true);
		showBanner($("upload-banner"), "loading", "업로드 중…");
		fillErrorList($("upload-errors"), [], false);
		$("upload-result").classList.add("hidden");
		try {
			var formData = U.buildUploadFormData(file);
			var path = U.uploadPath(kind);
			var result = await apiPostFile(path, formData);
			var interpreted = U.interpretUploadHttp(result.status, result.body);
			if (interpreted.ok) {
				noteOriginalsMayHaveChanged();
				showBanner($("upload-banner"), "ok", "업로드했습니다. 이전에 본 집계·원본·검토는 다시 조회하세요.");
				fillErrorList($("upload-errors"), [], false);
				renderUploadResult(interpreted.upload || {});
				uploadsPage = 0;
				await loadUploads();
				return;
			}
			var msg = interpreted.message;
			if (interpreted.tooLarge) {
				msg = interpreted.message + " " + interpreted.sizeHint;
			}
			if (sizeNote && interpreted.tooLarge) {
				msg = interpreted.message + " " + interpreted.sizeHint;
			}
			if (interpreted.notSaved) {
				msg = msg + " " + interpreted.notSaved;
			}
			showBanner($("upload-banner"), "error", msg);
			fillErrorList($("upload-errors"), interpreted.fieldErrors, interpreted.truncated);
		} catch (e) {
			if (e.kind === "network") {
				var unknown = U.interpretUploadNetworkError();
				showBanner($("upload-banner"), "warn", unknown.message);
				fillErrorList($("upload-errors"), [], false);
			} else {
				showBanner($("upload-banner"), "error", e.message + " " + U.notSavedMessage());
			}
		} finally {
			setUploading(false);
		}
	}

	function uploadsListPath() {
		var q = "page=" + uploadsPage + "&size=" + PAGE_SIZE;
		if (uploadsFilter === "BUSINESS" || uploadsFilter === "BANK") {
			q += "&fileType=" + encodeURIComponent(uploadsFilter);
		}
		return "/api/v1/uploads?" + q;
	}

	function renderUploadDetail(row) {
		var box = $("upload-detail");
		box.classList.remove("hidden");
		box.replaceChildren();
		function line(label, value) {
			var p = document.createElement("p");
			p.textContent = label + ": " + (value == null || value === "" ? "—" : String(value));
			box.appendChild(p);
		}
		line("업로드 번호", row.uploadId);
		line("파일명", row.originalFilename);
		line("유형", window.ReconUploadClient.fileTypeLabel(row.fileType));
		line("행 수", row.rowCount);
		line("업로드 시각", formatInstant(row.uploadedAt));
	}

	async function loadUploadDetail(id) {
		selectedUploadId = id;
		try {
			var row = await apiGet("/api/v1/uploads/" + id);
			if (selectedUploadId !== id) {
				return;
			}
			renderUploadDetail(row);
		} catch (e) {
			showBanner($("uploads-banner"), "error", e.kind === "network" ? e.message : "상세 조회에 실패했습니다. " + e.message);
		}
	}

	async function loadUploads() {
		var seq = ++loadSeq;
		var banner = $("uploads-banner");
		var tbody = $("uploads-body");
		tbody.replaceChildren();
		$("upload-detail").classList.add("hidden");
		showBanner(banner, "loading", "불러오는 중…");
		document.querySelectorAll(".filter-btn").forEach(function (btn) {
			btn.classList.toggle("active", (btn.getAttribute("data-upload-filter") || "") === uploadsFilter);
		});
		try {
			var data = await apiGet(uploadsListPath());
			if (seq !== loadSeq) {
				return;
			}
			var rows = (data && data.content) ? data.content : [];
			if (rows.length === 0) {
				showBanner(banner, "empty", "조회된 업로드가 없습니다.");
			} else {
				hideBanner(banner);
			}
			rows.forEach(function (row) {
				var tr = document.createElement("tr");
				tr.className = "clickable";
				tr.tabIndex = 0;
				if (selectedUploadId === row.uploadId) {
					tr.classList.add("selected");
				}
				tr.addEventListener("click", function () {
					document.querySelectorAll("#uploads-body tr").forEach(function (el) {
						el.classList.remove("selected");
					});
					tr.classList.add("selected");
					loadUploadDetail(row.uploadId);
				});
				tr.addEventListener("keydown", function (ev) {
					if (ev.key === "Enter" || ev.key === " ") {
						ev.preventDefault();
						tr.click();
					}
				});
				tr.appendChild(td(row.originalFilename, "clip"));
				tr.appendChild(td(window.ReconUploadClient.fileTypeLabel(row.fileType)));
				tr.appendChild(td(String(row.rowCount), "num"));
				tr.appendChild(td(formatInstant(row.uploadedAt)));
				tbody.appendChild(tr);
			});
			renderPager($("uploads-pager"), data.page, data.size, data.totalElements, function () {
				uploadsPage = Math.max(0, uploadsPage - 1);
				loadUploads();
			}, function () {
				uploadsPage += 1;
				loadUploads();
			});
		} catch (e) {
			if (seq !== loadSeq) {
				return;
			}
			showBanner(banner, "error", e.kind === "network" ? e.message : "조회에 실패했습니다. " + e.message);
		}
	}

	function route() {
		var r = parseHash();
		setNav(r.view);
		if (r.view === "monthly") {
			loadMonthly();
			return;
		}
		if (r.view === "day") {
			if (!/^\d{4}-\d{2}-\d{2}$/.test(r.date)) {
				showBanner($("day-banner"), "error", "날짜 형식이 올바르지 않습니다.");
				$("view-day").classList.remove("hidden");
				return;
			}
			loadDay(r.date);
			return;
		}
		if (r.view === "upload") {
			return;
		}
		if (r.view === "uploads") {
			loadUploads();
			return;
		}
		if (r.view === "vendors") {
			loadVendors();
			return;
		}
		loadDaily();
	}

	document.addEventListener("DOMContentLoaded", function () {
		if (!$("daily-from").value) {
			$("daily-from").value = "2026-09-01";
		}
		if (!$("daily-to").value) {
			$("daily-to").value = "2026-09-30";
		}
		if (!$("month-input").value) {
			$("month-input").value = "2026-09";
		}
		$("btn-daily").addEventListener("click", function () {
			location.hash = "#/daily";
			loadDaily();
		});
		$("btn-monthly").addEventListener("click", function () {
			location.hash = "#/monthly";
			loadMonthly();
		});
		$("btn-reload-day").addEventListener("click", function () {
			if (dayState) {
				loadDay(dayState.date);
			}
		});
		$("btn-save").addEventListener("click", saveReview);
		$("btn-back").addEventListener("click", function () {
			location.hash = "#/daily";
		});
		$("vendor-form").addEventListener("submit", saveVendor);
		$("btn-vendor-cancel").addEventListener("click", function () {
			resetVendorCreate();
		});
		$("upload-form").addEventListener("submit", submitUpload);
		document.querySelectorAll(".filter-btn").forEach(function (btn) {
			btn.addEventListener("click", function () {
				uploadsFilter = btn.getAttribute("data-upload-filter") || "";
				uploadsPage = 0;
				selectedUploadId = null;
				loadUploads();
			});
		});
		window.addEventListener("hashchange", route);
		if (!location.hash) {
			location.hash = "#/daily";
		} else {
			route();
		}
	});
})();
