package com.toolize.config;

import com.toolize.service.ProjectService;
import com.toolize.service.ToolUsageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * On application startup, rebuild the dynamic MCP tool registry
 * from the persisted projects.json file, so previously imported
 * APIs are immediately available again after a container restart.
 */
@Component
public class StartupInitializer implements CommandLineRunner {

    private final ProjectService projectService;
    private final ToolUsageService toolUsageService;

    public StartupInitializer(ProjectService projectService, ToolUsageService toolUsageService) {
        this.projectService = projectService;
        this.toolUsageService = toolUsageService;
    }

    @Override
    public void run(String... args) {
        projectService.rebuildRegistryFromDisk();
        toolUsageService.load();
    }
}
