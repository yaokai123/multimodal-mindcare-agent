package com.multimodalAgent.agent.config;

import com.multimodalAgent.agent.domain.UserAccount;
import com.multimodalAgent.agent.repository.UserAccountRepository;
import com.multimodalAgent.agent.service.RiskTicketService;
import com.multimodalAgent.agent.service.knowledge.KnowledgeIngestionService;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements ApplicationRunner {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final KnowledgeIngestionService knowledgeIngestionService;
    private final multimodalAgentProperties properties;
    private final RiskTicketService riskTicketService;

    public DataInitializer(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            KnowledgeIngestionService knowledgeIngestionService,
            multimodalAgentProperties properties,
            RiskTicketService riskTicketService
    ) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.knowledgeIngestionService = knowledgeIngestionService;
        this.properties = properties;
        this.riskTicketService = riskTicketService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 首次启动准备演示账号和内置知识库；已有数据时不会覆盖。
        if (properties.getSecurity().isSeedDemoUsers()) {
            seedUsers();
        }
        knowledgeIngestionService.ingestClasspathKnowledgeIfEmpty();
        riskTicketService.backfillHighRiskTickets();
    }

    private void seedUsers() {
        if (userAccountRepository.count() > 0) {
            return;
        }
        // 管理员账号用于后台查看，学生账号用于正常聊天体验。
        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setDisplayName("Counselor Admin");
        admin.setPassword(passwordEncoder.encode(requiredPassword(
                properties.getSecurity().getAdminPassword(),
                "MULTIMODAL_AGENT_ADMIN_PASSWORD")));
        admin.setRoles(Set.of("ROLE_ADMIN", "ROLE_USER"));
        userAccountRepository.save(admin);

        UserAccount student = new UserAccount();
        student.setUsername("student");
        student.setDisplayName("student");
        student.setPassword(passwordEncoder.encode(requiredPassword(
                properties.getSecurity().getStudentPassword(),
                "MULTIMODAL_AGENT_STUDENT_PASSWORD")));
        student.setRoles(Set.of("ROLE_USER"));
        userAccountRepository.save(student);
    }

    private String requiredPassword(String password, String envName) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(envName + " must be set when demo user seeding is enabled.");
        }
        if (password.length() < 12) {
            throw new IllegalStateException(envName + " must contain at least 12 characters.");
        }
        return password;
    }
}
