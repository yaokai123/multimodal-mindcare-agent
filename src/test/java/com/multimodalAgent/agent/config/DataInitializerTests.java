package com.multimodalAgent.agent.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.multimodalAgent.agent.domain.UserAccount;
import com.multimodalAgent.agent.repository.UserAccountRepository;
import com.multimodalAgent.agent.service.RiskTicketService;
import com.multimodalAgent.agent.service.knowledge.KnowledgeIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class DataInitializerTests {

    @Test
    void doesNotSeedDemoUsersByDefault() {
        UserAccountRepository userRepository = mock(UserAccountRepository.class);
        KnowledgeIngestionService knowledgeIngestionService = mock(KnowledgeIngestionService.class);
        DataInitializer initializer = new DataInitializer(
                userRepository,
                new BCryptPasswordEncoder(),
                knowledgeIngestionService,
                new multimodalAgentProperties(),
                mock(RiskTicketService.class));

        initializer.run(null);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any(UserAccount.class));
        verify(knowledgeIngestionService).ingestClasspathKnowledgeIfEmpty();
    }
}
