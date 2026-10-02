package com.smallbiz.reconciliation.upload;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

final class BankTransactionFingerprint {

	static final String PREFIX = "W1.";
	private static final DateTimeFormatter CANONICAL_TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss")
			.withResolverStyle(ResolverStyle.STRICT);

	private BankTransactionFingerprint() {
	}

	static boolean isReserved(String sourceLineId) {
		return sourceLineId != null && sourceLineId.matches("^W1\\.[0-9a-f]{64}$");
	}

	static String from(LocalDateTime bookedAtSeoul, BankDirection direction, long amount, long balanceAfter) {
		String canonical = "W1|"
				+ bookedAtSeoul.format(CANONICAL_TIME)
				+ "|"
				+ direction.name()
				+ "|"
				+ amount
				+ "|"
				+ balanceAfter;
		return PREFIX + UploadFileSupport.sha256Hex(canonical.getBytes(StandardCharsets.UTF_8));
	}
}
