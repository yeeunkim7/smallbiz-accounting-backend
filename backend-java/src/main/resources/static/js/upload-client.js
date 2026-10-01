(function (global) {
	"use strict";

	var MAX_FILE_BYTES = 5 * 1024 * 1024;
	var FILE_FIELD = "file";

	function uploadPath(kind) {
		if (kind === "BANK") {
			return "/api/v1/uploads/bank";
		}
		if (kind === "BUSINESS") {
			return "/api/v1/uploads/business";
		}
		return null;
	}

	function fileTypeLabel(type) {
		if (type === "BUSINESS") {
			return "업무 CSV";
		}
		if (type === "BANK") {
			return "은행 CSV";
		}
		return type || "—";
	}

	function convenienceUploadCheck(kind, file) {
		var fieldErrors = [];
		if (kind !== "BUSINESS" && kind !== "BANK") {
			fieldErrors.push({ field: "fileType", message: "업무 CSV 또는 은행 CSV를 선택하세요." });
		}
		if (!file) {
			fieldErrors.push({ field: FILE_FIELD, message: "업로드할 파일이 없습니다." });
		}
		return {
			ok: fieldErrors.length === 0,
			fieldErrors: fieldErrors,
			message: fieldErrors.length ? "요청 값이 올바르지 않습니다." : null
		};
	}

	function convenienceSizeNote(file) {
		if (file && file.size > MAX_FILE_BYTES) {
			return "파일이 5MiB를 넘습니다. 서버가 413으로 거절할 수 있습니다.";
		}
		return null;
	}

	function buildUploadFormData(file) {
		var fd = new FormData();
		fd.append(FILE_FIELD, file);
		return fd;
	}

	function formDataHasFileField(formData) {
		if (!formData || typeof formData.get !== "function") {
			return false;
		}
		return formData.get(FILE_FIELD) != null;
	}

	function describeUploadRequest(kind, formData) {
		return {
			method: "POST",
			path: uploadPath(kind),
			headers: { Accept: "application/json" },
			omitContentType: true,
			body: formData,
			fileField: FILE_FIELD
		};
	}

	function formatFieldError(item) {
		if (!item) {
			return "";
		}
		var parts = [];
		if (item.rowNumber != null && item.rowNumber !== "") {
			parts.push("CSV 레코드 " + item.rowNumber);
		}
		if (item.field) {
			parts.push(item.field);
		}
		if (item.message) {
			parts.push(item.message);
		}
		return parts.join(" · ");
	}

	function notSavedMessage() {
		return "이번 요청은 저장되지 않았습니다.";
	}

	function unknownNetworkMessage() {
		return "네트워크 오류로 업로드 결과를 확인하지 못했습니다. 실패했다고 단정하지 말고 업로드 이력에서 해당 파일이 있는지 확인하세요. 자동으로 다시 보내지는 않습니다.";
	}

	function interpretUploadHttp(httpStatus, body) {
		if (httpStatus >= 200 && httpStatus < 300) {
			return {
				ok: true,
				saved: true,
				upload: body,
				fieldErrors: [],
				truncated: false,
				message: null
			};
		}
		var fieldErrors = (body && body.fieldErrors) ? body.fieldErrors : [];
		var truncated = !!(body && body.truncated);
		var code = body && body.code;
		var message = (body && body.message) ? body.message : "요청을 처리하지 못했습니다.";
		if (httpStatus === 413 || code === "PAYLOAD_TOO_LARGE") {
			return {
				ok: false,
				saved: false,
				conflict: false,
				tooLarge: true,
				truncated: truncated,
				fieldErrors: fieldErrors,
				message: message || "파일 크기가 허용 한도를 초과했습니다.",
				notSaved: notSavedMessage(),
				sizeHint: "CSV 본문은 5MiB까지입니다."
			};
		}
		if (httpStatus === 409 || code === "DUPLICATE_UPLOAD_FILE" || code === "DUPLICATE_SOURCE_LINE_ID") {
			return {
				ok: false,
				saved: false,
				conflict: true,
				tooLarge: false,
				truncated: truncated,
				fieldErrors: fieldErrors,
				code: code,
				message: message,
				notSaved: notSavedMessage()
			};
		}
		if (httpStatus === 400) {
			return {
				ok: false,
				saved: false,
				conflict: false,
				tooLarge: false,
				truncated: truncated,
				fieldErrors: fieldErrors,
				message: message,
				notSaved: notSavedMessage()
			};
		}
		return {
			ok: false,
			saved: false,
			conflict: false,
			tooLarge: false,
			truncated: truncated,
			fieldErrors: fieldErrors,
			message: message,
			notSaved: notSavedMessage()
		};
	}

	function interpretUploadNetworkError() {
		return {
			ok: false,
			saved: null,
			unknown: true,
			fieldErrors: [],
			truncated: false,
			message: unknownNetworkMessage(),
			notSaved: null
		};
	}

	global.ReconUploadClient = {
		MAX_FILE_BYTES: MAX_FILE_BYTES,
		FILE_FIELD: FILE_FIELD,
		uploadPath: uploadPath,
		fileTypeLabel: fileTypeLabel,
		convenienceUploadCheck: convenienceUploadCheck,
		convenienceSizeNote: convenienceSizeNote,
		buildUploadFormData: buildUploadFormData,
		formDataHasFileField: formDataHasFileField,
		describeUploadRequest: describeUploadRequest,
		formatFieldError: formatFieldError,
		notSavedMessage: notSavedMessage,
		unknownNetworkMessage: unknownNetworkMessage,
		interpretUploadHttp: interpretUploadHttp,
		interpretUploadNetworkError: interpretUploadNetworkError
	};
})(window);
