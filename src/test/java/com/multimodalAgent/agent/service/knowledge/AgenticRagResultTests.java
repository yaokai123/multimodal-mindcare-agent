package com.multimodalAgent.agent.service.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class AgenticRagResultTests {

    @Test
    void exposesCitationsWithCategoryAndExcerpt() {
        AgenticRagResult result = new AgenticRagResult(
                "plan",
                List.of("query"),
                List.of(new SearchResult(7L, "risk-policy.md", "CRISIS_POLICY", "A".repeat(240), 0.82)),
                "review",
                true);

        var citations = result.citations();

        assertThat(citations).hasSize(1);
        assertThat(citations.get(0).chunkId()).isEqualTo(7L);
        assertThat(citations.get(0).source()).isEqualTo("risk-policy.md");
        assertThat(citations.get(0).category()).isEqualTo("CRISIS_POLICY");
        assertThat(citations.get(0).excerpt()).endsWith("...");
    }
}
