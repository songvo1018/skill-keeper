package com.skillskeeper.skillskeeper.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One PostgreSQL container for the whole test run, handing every test class its own database.
 *
 * <p>The container is a static singleton rather than a {@code @ServiceConnection} bean because
 * {@link AuthenticatedApiTest} discards its context after each class: a container bean would then
 * be started and stopped once per test class instead of once per run.
 *
 * <p>A database per class, rather than clearing the table between classes, is what keeps the
 * classes independent. Clearing cannot work here: rows are created while the context starts, by the
 * import of metadata left on disk, so any clean-up that runs after the context is up would delete
 * exactly what the test came to check, and one that runs before it would have nothing to delete.
 */
public final class PostgresTestContainer {

	private static final String IMAGE = "postgres:18";
	private static final String JDBC_URL_PREFIX = "jdbc:postgresql://";
	private static final String HOST_PORT_SEPARATOR = ":";
	private static final String DATABASE_PATH_SEPARATOR = "/";
	private static final String DATABASE_NAME_PREFIX = "skills_keeper_test_";
	private static final String CREATE_DATABASE_PREFIX = "create database ";
	private static final String CREATE_DATABASE_FAILED_PREFIX = "Could not create the test database ";
	private static final String DATASOURCE_URL_PROPERTY_TEMPLATE = "spring.datasource.url=%s";
	private static final String DATASOURCE_USERNAME_PROPERTY_TEMPLATE = "spring.datasource.username=%s";
	private static final String DATASOURCE_PASSWORD_PROPERTY_TEMPLATE = "spring.datasource.password=%s";

	private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer(IMAGE);

	private static final AtomicInteger DATABASE_COUNTER = new AtomicInteger();

	static {
		CONTAINER.start();
	}

	private PostgresTestContainer() {
	}

	/**
	 * Creates a database nothing else uses and points the context being built at it. Call this from
	 * a {@code @DynamicPropertySource} method: it runs once per context, which is once per test
	 * class, and Flyway then builds the schema inside that fresh database.
	 */
	public static void registerProperties(DynamicPropertyRegistry registry) {
		String databaseName = DATABASE_NAME_PREFIX + DATABASE_COUNTER.incrementAndGet();
		createDatabase(databaseName);

		String jdbcUrl = jdbcUrl(databaseName);
		registry.add("spring.datasource.url", () -> jdbcUrl);
		registry.add("spring.datasource.username", CONTAINER::getUsername);
		registry.add("spring.datasource.password", CONTAINER::getPassword);
	}

	/**
	 * The same connection as {@link #registerProperties}, but as plain {@code key=value} strings,
	 * for a test that builds a {@code SpringApplication} by hand and so has no
	 * {@code DynamicPropertyRegistry} to register with.
	 *
	 * <p>Points at the container's own database rather than a fresh one: the caller of this is
	 * watching a context fail to start, so there is nothing to isolate.
	 */
	public static String[] datasourceProperties() {
		return new String[] {
				DATASOURCE_URL_PROPERTY_TEMPLATE.formatted(jdbcUrl(CONTAINER.getDatabaseName())),
				DATASOURCE_USERNAME_PROPERTY_TEMPLATE.formatted(CONTAINER.getUsername()),
				DATASOURCE_PASSWORD_PROPERTY_TEMPLATE.formatted(CONTAINER.getPassword()),
		};
	}

	private static void createDatabase(String databaseName) {
		try (Connection connection = DriverManager.getConnection(jdbcUrl(CONTAINER.getDatabaseName()),
				CONTAINER.getUsername(), CONTAINER.getPassword());
				Statement statement = connection.createStatement()) {
			statement.execute(CREATE_DATABASE_PREFIX + databaseName);
		}
		catch (SQLException e) {
			throw new IllegalStateException(CREATE_DATABASE_FAILED_PREFIX + databaseName, e);
		}
	}

	private static String jdbcUrl(String databaseName) {
		return JDBC_URL_PREFIX + CONTAINER.getHost() + HOST_PORT_SEPARATOR + CONTAINER.getFirstMappedPort()
				+ DATABASE_PATH_SEPARATOR + databaseName;
	}
}
