package com.toolize.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.toolize.domain.ApiProject;
import com.toolize.domain.ApiProjectEntity;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Persistence for imported API projects, backed by a real database (H2 by
 * default, or Postgres/MySQL/Oracle - see SetupController) instead of a
 * single hand-rolled JSON file. Each project is one row: {@link ApiProject}
 * is serialized to JSON only for the handful of fields that don't need their
 * own column (auth config, enabled endpoints, tool customizations, the raw
 * spec), so updating one project is a single-row write rather than a
 * rewrite of every project in the system.
 */
@Service
public class ProjectPersistenceService {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ApiProjectJpaRepository repository;

    public ProjectPersistenceService(ApiProjectJpaRepository repository) {
        this.repository = repository;
    }

    /**
     * Historically loaded the JSON file and warmed an in-memory cache; kept as
     * a distinct method (rather than folding into {@link #findAll()}) since
     * that's what {@code StartupInitializer} calls to rebuild the tool
     * registry, even though every read now goes straight to the database.
     */
    public List<ApiProject> loadAll() {
        return findAll();
    }

    public void save(ApiProject project) {
        repository.save(toEntity(project));
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public Optional<ApiProject> find(String id) {
        return repository.findById(id).map(this::toDomain);
    }

    public List<ApiProject> findAll() {
        return repository.findAll().stream().map(this::toDomain).toList();
    }

    private ApiProjectEntity toEntity(ApiProject project) {
        String json = mapper.writeValueAsString(project);
        return new ApiProjectEntity(project.getId(), project.getName(), project.getStatus().name(),
                project.getImportedAt(), json);
    }

    private ApiProject toDomain(ApiProjectEntity entity) {
        return mapper.readValue(entity.getDataJson(), ApiProject.class);
    }
}
