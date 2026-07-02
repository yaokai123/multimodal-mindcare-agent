package com.multimodalAgent.agent.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaInitializer implements ApplicationRunner, Ordered {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        widenKnowledgeTextColumns();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private void widenKnowledgeTextColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE knowledge_chunks MODIFY content LONGTEXT NOT NULL");
            jdbcTemplate.execute("ALTER TABLE knowledge_chunks MODIFY embedding_json LONGTEXT NULL");
        } catch (RuntimeException ignored) {
            // Hibernate may still be creating the table on a fresh database, or the active profile may not use MySQL.
        }
    }
}
