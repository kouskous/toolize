package com.toolize.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates a handful of plain, Toolize-branded environment variables into
 * the {@code spring.datasource.*} properties Spring Boot actually reads, so
 * operators configure a production database with vocabulary that has
 * nothing to do with the framework underneath:
 *
 * <pre>
 *   TOOLIZE_DB_TYPE     POSTGRESQL | MYSQL | ORACLE (unset/H2 = keep the embedded default)
 *   TOOLIZE_DB_HOST
 *   TOOLIZE_DB_PORT     optional, defaults to the type's standard port
 *   TOOLIZE_DB_NAME
 *   TOOLIZE_DB_USERNAME
 *   TOOLIZE_DB_PASSWORD
 * </pre>
 *
 * These are read the same way regardless of how the process was started -
 * a plain {@code docker run -e}, a Kubernetes Deployment's env list backed
 * by a Secret, systemd, etc. - so every replica of a horizontally scaled
 * deployment picks up the exact same database without relying on a shared
 * local file that a freshly scheduled pod might never see.
 *
 * Runs as an {@link EnvironmentPostProcessor} (registered in
 * META-INF/spring.factories) so the translated properties are in place
 * before the datasource bean is ever created.
 */
public class DatasourceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String type = environment.getProperty("TOOLIZE_DB_TYPE");
        if (type == null || type.isBlank() || "H2".equalsIgnoreCase(type)) {
            return;
        }
        String normalizedType = type.trim().toUpperCase();

        String host = environment.getProperty("TOOLIZE_DB_HOST", "localhost");
        String port = environment.getProperty("TOOLIZE_DB_PORT", defaultPort(normalizedType));
        String database = environment.getProperty("TOOLIZE_DB_NAME", "toolize");
        String username = environment.getProperty("TOOLIZE_DB_USERNAME", "");
        String password = environment.getProperty("TOOLIZE_DB_PASSWORD", "");

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("spring.datasource.url", buildUrl(normalizedType, host, port, database));
        props.put("spring.datasource.username", username);
        props.put("spring.datasource.password", password);
        props.put("spring.datasource.driver-class-name", driverClassName(normalizedType));

        environment.getPropertySources().addFirst(new MapPropertySource("toolizeDatasourceEnv", props));
    }

    private String buildUrl(String type, String host, String port, String database) {
        return switch (type) {
            case "POSTGRESQL" -> "jdbc:postgresql://%s:%s/%s".formatted(host, port, database);
            case "MYSQL" -> "jdbc:mysql://%s:%s/%s".formatted(host, port, database);
            case "ORACLE" -> "jdbc:oracle:thin:@//%s:%s/%s".formatted(host, port, database);
            default -> throw new IllegalStateException(
                    "Unsupported TOOLIZE_DB_TYPE: " + type + " (expected POSTGRESQL, MYSQL, or ORACLE)");
        };
    }

    private String driverClassName(String type) {
        return switch (type) {
            case "POSTGRESQL" -> "org.postgresql.Driver";
            case "MYSQL" -> "com.mysql.cj.jdbc.Driver";
            case "ORACLE" -> "oracle.jdbc.OracleDriver";
            default -> throw new IllegalStateException("Unsupported TOOLIZE_DB_TYPE: " + type);
        };
    }

    private String defaultPort(String type) {
        return switch (type) {
            case "POSTGRESQL" -> "5432";
            case "MYSQL" -> "3306";
            case "ORACLE" -> "1521";
            default -> throw new IllegalStateException("Unsupported TOOLIZE_DB_TYPE: " + type);
        };
    }
}
