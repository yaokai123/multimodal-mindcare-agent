package com.multimodalAgent.agent.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.multimodalAgent.agent.config.multimodalAgentProperties;
import com.multimodalAgent.agent.service.AuditService;
import com.multimodalAgent.agent.service.knowledge.KnowledgeFileService;
import com.multimodalAgent.agent.service.knowledge.KnowledgeService;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.multipart.FilePart;

class KnowledgeControllerTests {

    @Test
    void rejectsUnsupportedKnowledgeFileExtensions() {
        KnowledgeController controller = new KnowledgeController(
                mock(KnowledgeService.class),
                mock(KnowledgeFileService.class),
                new multimodalAgentProperties(),
                mock(AuditService.class));
        FilePart file = mock(FilePart.class);
        org.mockito.Mockito.when(file.filename()).thenReturn("payload.exe");

        assertThatThrownBy(() -> controller.ingestFile(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported knowledge file type");
    }
}
