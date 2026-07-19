package com.toolize.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Locale;
import java.util.Properties;

/**
 * Lets an already-logged-in admin move Toolize off the zero-config embedded
 * H2 database onto a real Postgres/MySQL/Oracle instance, without rebuilding
 * the Docker image. The chosen connection is validated with a real JDBC
 * connection attempt, then written to {@code datasource.properties} in the
 * data directory - which {@code spring.config.import} picks up on the next
 * boot (see application.yml) - after which the app exits so the container's
 * restart policy (e.g. {@code --restart unless-stopped}) brings it back up
 * against the new database. Swapping a JPA datasource live, mid-request, is
 * far more fragile than a clean restart, so that's the deliberate trade-off
 * here.
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private static final Logger log = LoggerFactory.getLogger(SetupController.class);

    private final Environment environment;

    @Value("${toolize.data-dir:/data}")
    private String dataDir;

    public SetupController(Environment environment) {
        this.environment = environment;
    }

    public enum DatabaseType {
        POSTGRESQL,
        MYSQL,
        ORACLE
    }

    public record DatasourceRequest(DatabaseType type, String host, int port, String database,
                                     String username, String password) {
    }

    public record TestResult(boolean success, String message) {
    }

    public record StatusView(boolean configured, String activeType) {
    }

    @GetMapping("/status")
    public StatusView status() {
        boolean configured = Files.exists(overrideFile());
        String url = environment.getProperty("spring.datasource.url", "");
        return new StatusView(configured, detectType(url));
    }

    @PostMapping("/datasource/test")
    public ResponseEntity<TestResult> test(@RequestBody DatasourceRequest request) {
        return ResponseEntity.ok(testConnection(request));
    }

    @PostMapping("/datasource")
    public ResponseEntity<TestResult> save(@RequestBody DatasourceRequest request) {
        TestResult result = testConnection(request);
        if (!result.success()) {
            return ResponseEntity.badRequest().body(result);
        }

        try {
            Properties props = new Properties();
            props.setProperty("spring.datasource.url", buildUrl(request));
            props.setProperty("spring.datasource.username", request.username() != null ? request.username() : "");
            props.setProperty("spring.datasource.password", request.password() != null ? request.password() : "");
            props.setProperty("spring.datasource.driver-class-name", driverClassName(request.type()));

            File dir = new File(dataDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            try (FileOutputStream out = new FileOutputStream(overrideFile().toFile())) {
                props.store(out, "Written by Toolize's Database setup screen - do not edit by hand while the app is running");
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new TestResult(false, "Could not save configuration: " + e.getMessage()));
        }

        log.info("Datasource configuration saved ({}); restarting to apply it", request.type());
        scheduleRestart();
        return ResponseEntity.ok(new TestResult(true,
                "Configuration saved. Toolize is restarting to apply it - this page will be unavailable for a few seconds."));
    }

    private TestResult testConnection(DatasourceRequest request) {
        String url = buildUrl(request);
        try (Connection connection = DriverManager.getConnection(url, request.username(), request.password())) {
            if (connection.isValid(5)) {
                return new TestResult(true, "Connection succeeded.");
            }
            return new TestResult(false, "Connected, but the database did not respond in time.");
        } catch (Exception e) {
            return new TestResult(false, e.getMessage());
        }
    }

    private String buildUrl(DatasourceRequest request) {
        return switch (request.type()) {
            case POSTGRESQL -> "jdbc:postgresql://%s:%d/%s".formatted(request.host(), request.port(), request.database());
            case MYSQL -> "jdbc:mysql://%s:%d/%s".formatted(request.host(), request.port(), request.database());
            case ORACLE -> "jdbc:oracle:thin:@//%s:%d/%s".formatted(request.host(), request.port(), request.database());
        };
    }

    private String driverClassName(DatabaseType type) {
        return switch (type) {
            case POSTGRESQL -> "org.postgresql.Driver";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case ORACLE -> "oracle.jdbc.OracleDriver";
        };
    }

    private String detectType(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        if (lower.startsWith("jdbc:postgresql:")) {
            return "POSTGRESQL";
        }
        if (lower.startsWith("jdbc:mysql:")) {
            return "MYSQL";
        }
        if (lower.startsWith("jdbc:oracle:")) {
            return "ORACLE";
        }
        if (lower.startsWith("jdbc:h2:")) {
            return "H2";
        }
        return "UNKNOWN";
    }

    private Path overrideFile() {
        return Path.of(dataDir, "datasource.properties");
    }

    private void scheduleRestart() {
        Thread shutdownThread = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        });
        shutdownThread.setDaemon(true);
        shutdownThread.start();
    }
}
