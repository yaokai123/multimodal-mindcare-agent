package com.multimodalAgent.agent.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.service.mcp.multimodalAgentMcpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class McpControllerTests {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rejectsRequestsWhenMcpServerIsDisabled() {
        multimodalAgentProperties properties = new multimodalAgentProperties();
        McpController controller = controller(properties);

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.handle(null, objectMapper.createObjectNode()))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void rejectsRequestsWithInvalidMcpToken() {
        multimodalAgentProperties properties = new multimodalAgentProperties();
        properties.getMcp().getServer().setEnabled(true);
        properties.getMcp().getServer().setAuthToken("expected-token");
        McpController controller = controller(properties);

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> controller.handle("wrong-token", objectMapper.createObjectNode()))
                .satisfies(exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private McpController controller(multimodalAgentProperties properties) {
        return new McpController(new multimodalAgentMcpServer(objectMapper, properties), properties);
    }
}
