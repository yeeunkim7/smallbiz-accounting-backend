package com.smallbiz.reconciliation.vendor;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

/** No Spring application context, JDBC connections, migrations or fixture cleanup. */
class TestDatabaseIsolationTest {
    private static final String URL = "jdbc:postgresql://localhost:5432/reconciliation_test";
    private static final String USER = "chagok_test";

    @Test void permitsOnlyExplicitTestDatabaseAndSupportedConnectionOptions() {
        for (String url : List.of(URL, "jdbc:postgresql://localhost/reconciliation_test",
                "jdbc:postgresql://[::1]:5432/reconciliation_test",
                URL + "?sslmode=require&connectTimeout=5&socketTimeout=10")) {
            var calls = new AtomicInteger();
            TestDatabaseIsolation.validateThenConnect(url, USER,
                    "jdbc:postgresql://localhost/reconciliation_dev", calls::incrementAndGet);
            assertThat(calls).hasValue(1);
        }
    }

    @Test void refusesDevelopmentProductionAndUnknownDatabasesBeforeConnecting() {
        for (String db : List.of("reconciliation_dev", "production", "postgres", "other_test",
                "RECONCILIATION_TEST", "reconciliation_test_backup")) {
            rejected("jdbc:postgresql://localhost:5432/" + db, USER, null);
        }
    }

    @Test void refusesAmbiguousEncodedAndNonPostgresTargetsBeforeConnecting() {
        for (String url : List.of("jdbc:h2:mem:reconciliation_test", "jdbc:postgresql:reconciliation_test",
                "jdbc:postgresql://localhost/", URL + "/", URL + "#fragment",
                "jdbc:postgresql://localhost/reconciliation_%74est",
                "jdbc:postgresql://host1,host2/reconciliation_test",
                "jdbc:postgresql://localhost:99999/reconciliation_test")) {
            rejected(url, USER, null);
        }
    }

    @Test void refusesRoutingOptionsEvenWhenAllowedDatabaseAppearsLastInQuery() {
        for (String query : List.of("service=production", "PGDBNAME=production",
                "PGDBNAME=production&note=/reconciliation_test", "%50GDBNAME=production",
                "user=postgres", "currentSchema=public", "sslmode=require&service=production",
                "sslmode=require&sslmode=disable", "connectTimeout=5&", "")) {
            rejected(URL + "?" + query, USER, null);
        }
    }

    @Test void refusesSameDevelopmentTargetDespiteDefaultPortHostCaseOrOptions() {
        for (String dev : List.of(URL, "jdbc:postgresql://LOCALHOST/reconciliation_test",
                URL + "?sslmode=require")) rejected(URL, USER, dev);
    }

    @Test void refusesMissingOrNumericRoleBeforeConnecting() {
        for (String user : List.of("", " ", "12345")) rejected(URL, user, null);
        rejected(URL, null, null);
    }

    @Test void malformedUrlErrorsDoNotExposeCredentialContents() {
        String secretMarker = "not-a-real-secret";
        assertThatThrownBy(() -> TestDatabaseIsolation.validateThenConnect(
                "jdbc:postgresql://user:" + secretMarker + "@localhost/reconciliation_test", USER, null,
                () -> fail("Must not connect")))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(secretMarker).hasNoCause();
    }

    @Test void effectiveSpringDatasourceMustMatchValidatedEnvironment() {
        var env = environment();
        assertThatCode(() -> TestDatabaseIsolation.validateEffectiveConfiguration(env, URL, USER))
                .doesNotThrowAnyException();
        env.setProperty("spring.datasource.url", "jdbc:postgresql://localhost/production");
        assertThatThrownBy(() -> TestDatabaseIsolation.validateEffectiveConfiguration(env, URL, USER))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test void refusesEffectiveUsernameOverrideAndSeparateFlywayOrHikariConnections() {
        var env = environment().withProperty("spring.datasource.username", "postgres");
        assertThatThrownBy(() -> TestDatabaseIsolation.validateEffectiveConfiguration(env, URL, USER))
                .isInstanceOf(IllegalStateException.class);
        for (String key : List.of("spring.flyway.url", "spring.flyway.user",
                "spring.datasource.hikari.jdbc-url", "spring.datasource.hikari.username",
                "spring.datasource.jndi-name", "spring.datasource.type", "spring.datasource.driver-class-name",
                "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.data-source-properties.databaseName",
                "spring.datasource.hikari.data-source-properties.serverName")) {
            var overridden = environment().withProperty(key, "not-permitted");
            assertThatThrownBy(() -> TestDatabaseIsolation.validateEffectiveConfiguration(overridden, URL, USER))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void refusesCombinedHikariDataSourceClassAndNestedDatabaseOverride() {
        var env = environment()
                .withProperty("spring.datasource.hikari.data-source-class-name", "org.postgresql.ds.PGSimpleDataSource")
                .withProperty("spring.datasource.hikari.data-source-properties.databaseName", "reconciliation_dev");
        assertThatThrownBy(() -> TestDatabaseIsolation.validateEffectiveConfiguration(env, URL, USER))
                .isInstanceOf(IllegalStateException.class);
    }

    private static MockEnvironment environment() {
        return new MockEnvironment().withProperty("spring.datasource.url", URL)
                .withProperty("spring.datasource.username", USER);
    }

    private static void rejected(String url, String user, String dev) {
        var calls = new AtomicInteger();
        assertThatThrownBy(() -> TestDatabaseIsolation.validateThenConnect(url, user, dev, calls::incrementAndGet))
                .isInstanceOf(IllegalStateException.class);
        assertThat(calls).as("invalid configuration must be rejected before JDBC connection").hasValue(0);
    }
}
