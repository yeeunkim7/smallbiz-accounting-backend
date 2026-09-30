package com.smallbiz.reconciliation.vendor;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class TestDatabaseIsolation {

	private TestDatabaseIsolation() {
	}

	public static void assertNotDevelopmentDatabase() {
		String testUrl = requiredEnv("TEST_DB_URL");
		String username = requiredEnv("TEST_DB_USERNAME");
		requiredEnv("TEST_DB_PASSWORD");
		String devUrl = System.getenv("DB_URL");
		if (devUrl != null && !devUrl.isBlank() && testUrl.equals(devUrl)) {
			throw new IllegalStateException("TEST_DB_URL must not be the same as DB_URL.");
		}
		String testDatabase = jdbcDatabaseName(testUrl);
		if ("reconciliation_dev".equals(testDatabase)) {
			throw new IllegalStateException("TEST_DB_URL must not point at reconciliation_dev.");
		}
		if (username.chars().allMatch(Character::isDigit)) {
			throw new IllegalStateException(
					"TEST_DB_USERNAME must be the PostgreSQL role name, not a number. "
							+ "The password was probably entered as the username."
			);
		}
		assertCredentialsAccepted(testUrl, username, System.getenv("TEST_DB_PASSWORD"));
	}

	private static void assertCredentialsAccepted(String url, String username, String password) {
		try {
			Class.forName("org.postgresql.Driver");
			try (Connection ignored = DriverManager.getConnection(url, username, password)) {
				return;
			}
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalStateException("PostgreSQL JDBC driver is required for integration tests.");
		}
		catch (SQLException ex) {
			if ("28P01".equals(ex.getSQLState())) {
				throw new IllegalStateException(
						"PostgreSQL rejected TEST_DB_USERNAME/TEST_DB_PASSWORD (SQLState 28P01). "
								+ "Use the database role that can connect to reconciliation_test. "
								+ "Do not put the password in TEST_DB_USERNAME."
				);
			}
			throw new IllegalStateException("Cannot connect to TEST_DB_URL for integration tests.", ex);
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
