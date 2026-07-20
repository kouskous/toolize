package com.toolize.config;

import com.toolize.service.ProjectService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * On application startup, rebuild the dynamic MCP tool registry
 * from the persisted projects, so previously imported APIs are
 * immediately available again after a container restart.
 */
@Component
public class StartupInitializer implements CommandLineRunner {

    private final ProjectService projectService;

    public StartupInitializer(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public void run(String... args) {
        projectService.rebuildRegistryFromDisk();
    }
}
