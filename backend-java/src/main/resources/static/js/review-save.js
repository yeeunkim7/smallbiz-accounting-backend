(function (global) {
	"use strict";

	function versionsChanged(before, after) {
		return Number(before) !== Number(after);
	}

	function allowedPutStatus(status) {
		return status === "UNREVIEWED" || status === "REVIEWED";
	}

	function buildPutPayload(status, memo, version) {
		if (!allowedPutStatus(status)) {
			throw new Error("저장할 수 없는 검토 상태입니다.");
		}
		var payload = { status: status, version: Number(version) };
		if (memo != null && String(memo).trim() !== "") {
			payload.memo = String(memo);
		} else {
			payload.memo = null;
		}
		return payload;
	}

	function interpretPutHttp(httpStatus, body) {
		var code = body && body.code;
		if (httpStatus === 409 || code === "REVIEW_VERSION_CONFLICT") {
			return {
				ok: false,
				conflict: true,
				reloadRequired: true,
				message: "다른 작업으로 검토가 바뀌었습니다. 다시 조회한 뒤 저장하세요."
			};
		}
		if (httpStatus >= 200 && httpStatus < 300) {
			return { ok: true, conflict: false, reloadRequired: false, review: body };
		}
		return {
			ok: false,
			conflict: false,
			reloadRequired: false,
			message: (body && body.message) ? body.message : "저장에 실패했습니다."
		};
	}

	function putFromLoadedReview(loaded, putStatus, memo) {
		return buildPutPayload(putStatus, memo, loaded && loaded.version);
	}

	global.ReconReviewSave = {
		versionsChanged: versionsChanged,
		allowedPutStatus: allowedPutStatus,
		buildPutPayload: buildPutPayload,
		putFromLoadedReview: putFromLoadedReview,
		interpretPutHttp: interpretPutHttp
	};
})(window);
