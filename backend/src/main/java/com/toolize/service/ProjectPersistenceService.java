package com.toolize.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.toolize.domain.ApiProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple filesystem persistence for imported API projects.
 * No database is used in V1: projects are stored as a single JSON
 * file (data/projects.json) so they survive container restarts.
 */
@Service
public class ProjectPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ProjectPersistenceService.class);

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ConcurrentHashMap<String, ApiProject> projects = new ConcurrentHashMap<>();

    @Value("${toolize.data-dir:/data}")
    private String dataDir;

    private Path storageFile() {
        return Path.of(dataDir, "projects.json");
    }

    public synchronized List<ApiProject> loadAll() {
        Path file = storageFile();
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            ApiProject[] loaded = mapper.readValue(file.toFile(), ApiProject[].class);
            for (ApiProject p : loaded) {
                projects.put(p.getId(), p);
            }
            return List.of(loaded);
        } catch (JacksonException e) {
            log.warn("Could not read {}: {}", file, e.getMessage());
            return new ArrayList<>();
        }
    }

    public synchronized void save(ApiProject project) {
        projects.put(project.getId(), project);
        persist();
    }

    public synchronized void delete(String id) {
        projects.remove(id);
        persist();
    }

    public Optional<ApiProject> find(String id) {
        return Optional.ofNullable(projects.get(id));
    }

    public List<ApiProject> findAll() {
        return List.copyOf(projects.values());
    }

    private void persist() {
        try {
            File dir = new File(dataDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(storageFile().toFile(), projects.values());
        } catch (JacksonException e) {
            log.error("Failed to persist projects.json: {}", e.getMessage());
        }
    }
}
