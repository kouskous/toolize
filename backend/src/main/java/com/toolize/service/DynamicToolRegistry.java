package com.toolize.service;

import com.toolize.domain.McpTool;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime registry of all MCP tools currently exposed by the server.
 * Backed by a ConcurrentHashMap so tools can be registered and removed
 * at any time, without restarting the server, and safely read concurrently
 * by MCP clients calling tools/list and tools/call.
 */
@Service
public class DynamicToolRegistry {

    private final ConcurrentHashMap<String, McpTool> tools = new ConcurrentHashMap<>();

    public void register(McpTool tool) {
        tools.put(tool.getName(), tool);
    }

    public void registerAll(List<McpTool> newTools) {
        newTools.forEach(this::register);
    }

    public void removeAllForProject(String projectId) {
        tools.values().removeIf(t -> t.getProjectId().equals(projectId));
    }

    public Optional<McpTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<McpTool> listAll() {
        return List.copyOf(tools.values());
    }

    public List<McpTool> listForProject(String projectId) {
        return tools.values().stream()
                .filter(t -> t.getProjectId().equals(projectId))
                .toList();
    }

    public int count() {
        return tools.size();
    }

    public int countForProject(String projectId) {
        return (int) tools.values().stream().filter(t -> t.getProjectId().equals(projectId)).count();
    }
}
