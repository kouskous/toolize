package com.toolize.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA row for a single imported API project. Only the fields worth indexing
 * or inspecting directly in the database get their own column; everything
 * else (auth config, enabled endpoints, tool customizations, the raw spec...)
 * is serialized as one JSON blob in {@code dataJson} and round-tripped
 * through {@link ApiProject} by {@code ProjectPersistenceService}. This keeps
 * a single project update to a single-row write, instead of the previous
 * JSON-file approach which rewrote every project on every save.
 */
@Entity
@Table(name = "api_project")
public class ApiProjectEntity {

    @Id
    @Column(length = 255)
    private String id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private Instant importedAt;

    @Lob
    @Column(nullable = false)
    private String dataJson;

    protected ApiProjectEntity() {
        // JPA
    }

    public ApiProjectEntity(String id, String name, String status, Instant importedAt, String dataJson) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.importedAt = importedAt;
        this.dataJson = dataJson;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getStatus() {
        return status;
    }

    public Instant getImportedAt() {
        return importedAt;
    }

    public String getDataJson() {
        return dataJson;
    }
}
