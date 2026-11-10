package com.smallbiz.reconciliation.vendor;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.util.Set;
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
		validateThenConnect(testUrl, username, devUrl,
				() -> assertCredentialsAccepted(testUrl, username, System.getenv("TEST_DB_PASSWORD")));
	}

	// Spring properties may override the environment placeholders in application-test.yml.
	static void assertEffectiveConfiguration(org.springframework.core.env.Environment environment) {
		validateEffectiveConfiguration(environment, requiredEnv("TEST_DB_URL"), requiredEnv("TEST_DB_USERNAME"));
	}

	static void validateEffectiveConfiguration(org.springframework.core.env.Environment environment, String url, String username) {
		if (!url.equals(environment.getProperty("spring.datasource.url"))
				|| !username.equals(environment.getProperty("spring.datasource.username"))) {
			throw new IllegalStateException("Test datasource must use TEST_DB_URL and TEST_DB_USERNAME.");
		}
		for (String override : Set.of("spring.flyway.url", "spring.flyway.user",
				"spring.datasource.jndi-name", "spring.datasource.type",
				"spring.datasource.driver-class-name")) {
			if (environment.containsProperty(override))
				throw new IllegalStateException("Separate test datasource overrides are not permitted.");
		}
		// Hikari can choose a DataSource class instead of jdbcUrl, including nested
		// databaseName/serverName properties. Reject the whole alternate binding path.
		if (org.springframework.boot.context.properties.bind.Binder.get(environment)
				.bind("spring.datasource.hikari", org.springframework.boot.context.properties.bind.Bindable
						.mapOf(String.class, Object.class)).isBound())
			throw new IllegalStateException("Hikari overrides are not permitted for integration tests.");
	}

	static void validateThenConnect(String testUrl, String username, String devUrl, Runnable connect) {
		URI test = jdbcTarget(testUrl);
		if (!"/reconciliation_test".equals(test.getRawPath())) {
			throw new IllegalStateException("TEST_DB_URL must select reconciliation_test exactly.");
		}
		if (devUrl != null && !devUrl.isBlank()) {
			URI dev = jdbcTarget(devUrl);
			if (test.getHost().equalsIgnoreCase(dev.getHost()) && port(test) == port(dev)
					&& test.getRawPath().equals(dev.getRawPath()))
				throw new IllegalStateException("Test and development datasource targets must differ.");
		}
		if (username == null || username.isBlank() || username.chars().allMatch(Character::isDigit)) {
			throw new IllegalStateException("TEST_DB_USERNAME must be a PostgreSQL test role name.");
		}
		connect.run();
	}

	private static int port(URI url) { return url.getPort() == -1 ? 5432 : url.getPort(); }

	private static URI jdbcTarget(String jdbcUrl) {
		try {
			if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) throw new URISyntaxException("", "scheme");
			URI url = new URI(jdbcUrl.substring(5));
			if (url.getHost() == null || url.getUserInfo() != null || url.getRawFragment() != null
					|| port(url) < 1 || port(url) > 65535 || url.getRawPath() == null
					|| !url.getRawPath().matches("/[A-Za-z0-9_]+")) throw new URISyntaxException("", "target");
			// Reject service files, database/host overrides and encoded option names.
			// Deliberately support only connection options that cannot change the target.
			if (url.getRawQuery() != null) {
				Set<String> seen = new java.util.HashSet<>();
				for (String option : url.getRawQuery().split("&", -1)) {
					String[] pair = option.split("=", -1);
					if (pair.length != 2 || !seen.add(pair[0]) || !switch (pair[0]) {
						case "sslmode" -> Set.of("disable", "allow", "prefer", "require", "verify-ca", "verify-full").contains(pair[1]);
						case "connectTimeout", "socketTimeout" -> pair[1].matches("[0-9]{1,5}");
						default -> false;
					}) throw new URISyntaxException("", "option");
				}
			}
			return url;
		} catch (URISyntaxException ex) {
			// Do not attach the original exception: JDBC URLs can contain credentials.
			throw new IllegalStateException("Use an explicit PostgreSQL host/port/database URL with supported test options.");
		}
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

}
