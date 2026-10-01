(function (global) {
	"use strict";

	var CODE_PATTERN = /^[A-Z0-9_-]+$/;

	function settlementLabel(type) {
		if (type === "PREPAID") {
			return "선불";
		}
		if (type === "POSTPAID") {
			return "후불";
		}
		return type || "—";
	}

	function allowedSettlement(type) {
		return type === "PREPAID" || type === "POSTPAID";
	}

	function convenienceCreateCheck(vendorCode, vendorName, settlementType) {
		var fieldErrors = [];
		var code = vendorCode == null ? "" : String(vendorCode).trim();
		var name = vendorName == null ? "" : String(vendorName).trim();
		if (!code) {
			fieldErrors.push({ field: "vendorCode", message: "거래처 코드는 필수입니다." });
		} else if (code.length > 32) {
			fieldErrors.push({ field: "vendorCode", message: "거래처 코드는 32자 이하여야 합니다." });
		} else if (!CODE_PATTERN.test(code)) {
			fieldErrors.push({ field: "vendorCode", message: "거래처 코드는 영문 대문자, 숫자, 하이픈, 밑줄만 사용할 수 있습니다." });
		}
		if (!name) {
			fieldErrors.push({ field: "vendorName", message: "거래처명은 필수입니다." });
		} else if (name.length > 100) {
			fieldErrors.push({ field: "vendorName", message: "거래처명은 100자 이하여야 합니다." });
		}
		if (!allowedSettlement(settlementType)) {
			fieldErrors.push({ field: "settlementType", message: "정산 방식은 필수입니다." });
		}
		return {
			ok: fieldErrors.length === 0,
			fieldErrors: fieldErrors,
			message: fieldErrors.length ? "요청 값이 올바르지 않습니다." : null
		};
	}

	function convenienceUpdateCheck(vendorName, settlementType) {
		var fieldErrors = [];
		var name = vendorName == null ? "" : String(vendorName).trim();
		if (!name) {
			fieldErrors.push({ field: "vendorName", message: "거래처명은 필수입니다." });
		} else if (name.length > 100) {
			fieldErrors.push({ field: "vendorName", message: "거래처명은 100자 이하여야 합니다." });
		}
		if (!allowedSettlement(settlementType)) {
			fieldErrors.push({ field: "settlementType", message: "정산 방식은 필수입니다." });
		}
		return {
			ok: fieldErrors.length === 0,
			fieldErrors: fieldErrors,
			message: fieldErrors.length ? "요청 값이 올바르지 않습니다." : null
		};
	}

	function buildCreatePayload(vendorCode, vendorName, settlementType) {
		return {
			vendorCode: String(vendorCode).trim(),
			vendorName: String(vendorName).trim(),
			settlementType: settlementType
		};
	}

	function buildUpdatePayload(vendorName, settlementType) {
		return {
			vendorName: String(vendorName).trim(),
			settlementType: settlementType
		};
	}

	function describeCreateRequest(payload) {
		return {
			method: "POST",
			path: "/api/v1/vendors",
			headers: { "Content-Type": "application/json", Accept: "application/json" },
			body: payload
		};
	}

	function describeUpdateRequest(id, payload) {
		return {
			method: "PUT",
			path: "/api/v1/vendors/" + id,
			headers: { "Content-Type": "application/json", Accept: "application/json" },
			body: payload
		};
	}

	function interpretVendorHttp(httpStatus, body) {
		if (httpStatus >= 200 && httpStatus < 300) {
			return { ok: true, vendor: body, fieldErrors: [] };
		}
		var fieldErrors = (body && body.fieldErrors) ? body.fieldErrors : [];
		var message = (body && body.message) ? body.message : "요청을 처리하지 못했습니다.";
		if (httpStatus === 409 || (body && body.code === "DUPLICATE_VENDOR_CODE")) {
			return {
				ok: false,
				conflict: true,
				message: message || "이미 사용 중인 거래처 코드입니다.",
				fieldErrors: fieldErrors
			};
		}
		if (httpStatus === 400) {
			return { ok: false, conflict: false, message: message, fieldErrors: fieldErrors };
		}
		if (httpStatus === 404) {
			return { ok: false, conflict: false, message: message || "거래처를 찾을 수 없습니다.", fieldErrors: fieldErrors };
		}
		return { ok: false, conflict: false, message: message, fieldErrors: fieldErrors };
	}

	global.ReconVendorForms = {
		settlementLabel: settlementLabel,
		allowedSettlement: allowedSettlement,
		convenienceCreateCheck: convenienceCreateCheck,
		convenienceUpdateCheck: convenienceUpdateCheck,
		buildCreatePayload: buildCreatePayload,
		buildUpdatePayload: buildUpdatePayload,
		describeCreateRequest: describeCreateRequest,
		describeUpdateRequest: describeUpdateRequest,
		interpretVendorHttp: interpretVendorHttp
	};
})(window);
