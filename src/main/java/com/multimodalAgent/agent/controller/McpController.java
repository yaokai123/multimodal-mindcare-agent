package com.multimodalAgent.agent.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.service.mcp.multimodalAgentMcpServer;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
/**
 * MCP JSON-RPC 服务端入口。
 */
public class McpController {

    private static final String MCP_TOKEN_HEADER = "X-MCP-Token";

    private final multimodalAgentMcpServer mcpServer;
    private final multimodalAgentProperties properties;

    public McpController(multimodalAgentMcpServer mcpServer, multimodalAgentProperties properties) {
        this.mcpServer = mcpServer;
        this.properties = properties;
    }

    @PostMapping(value = "/mcp", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> handle(
            @RequestHeader(value = MCP_TOKEN_HEADER, required = false) String token,
            @RequestBody JsonNode request
    ) {
        verifyMcpAccess(token);
        return mcpServer.handle(request);
    }

    private void verifyMcpAccess(String token) {
        multimodalAgentProperties.Server server = properties.getMcp().getServer();
        if (!server.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP server is disabled.");
        }
        String expected = server.getAuthToken();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MCP server token is not configured.");
        }
        if (!expected.equals(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid MCP token.");
        }
    }
}
