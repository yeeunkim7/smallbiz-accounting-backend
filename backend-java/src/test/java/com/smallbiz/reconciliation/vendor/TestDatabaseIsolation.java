package com.smallbiz.reconciliation.vendor;

final class TestDatabaseIsolation {

	private TestDatabaseIsolation() {
	}

	static void assertNotDevelopmentDatabase() {
		String testUrl = requiredEnv("TEST_DB_URL");
		String devUrl = System.getenv("DB_URL");
		if (devUrl != null && !devUrl.isBlank() && testUrl.equals(devUrl)) {
			throw new IllegalStateException("TEST_DB_URL must not be the same as DB_URL.");
		}
		String testDatabase = jdbcDatabaseName(testUrl);
		if ("reconciliation_dev".equals(testDatabase)) {
			throw new IllegalStateException("TEST_DB_URL must not point at reconciliation_dev.");
		}
	}

	private static String requiredEnv(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " must be set for integration tests.");
		}
		return value;
	}

	private static String jdbcDatabaseName(String jdbcUrl) {
		int slash = jdbcUrl.lastIndexOf('/');
		if (slash < 0 || slash == jdbcUrl.length() - 1) {
			throw new IllegalStateException("TEST_DB_URL is not a JDBC URL with a database name.");
		}
		String name = jdbcUrl.substring(slash + 1);
		int query = name.indexOf('?');
		return (query >= 0 ? name.substring(0, query) : name).toLowerCase();
	}
}
