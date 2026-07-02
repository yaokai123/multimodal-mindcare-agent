package com.multimodalAgent.agent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "knowledge_chunks")
/**
 * 知识库切块。
 *
 * <p>每个上传文件会被拆成多个 chunk；sourceIndex 用于恢复相邻上下文。</p>
 */
public class KnowledgeChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 180)
    private String source;

    @Column(length = 80)
    private String category = "GENERAL";

    @Column(length = 240)
    private String tags = "";

    @Column(length = 120)
    private String audience = "ALL_STUDENTS";

    @Column(length = 40)
    private String riskLevel = "LOW";

    @Column(nullable = false, columnDefinition = "integer default 0")
    private int sourceIndex;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String embeddingJson;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int version = 1;

    @Column(length = 80)
    private String versionStatus = "ENABLED";

    @Column(length = 500)
    private String versionNote = "";

    private Instant disabledAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public int getSourceIndex() {
        return sourceIndex;
    }

    public void setSourceIndex(int sourceIndex) {
        this.sourceIndex = sourceIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getEmbeddingJson() {
        return embeddingJson;
    }

    public void setEmbeddingJson(String embeddingJson) {
        this.embeddingJson = embeddingJson;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.disabledAt = active ? null : Instant.now();
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getVersionStatus() {
        return versionStatus;
    }

    public void setVersionStatus(String versionStatus) {
        this.versionStatus = versionStatus;
    }

    public String getVersionNote() {
        return versionNote;
    }

    public void setVersionNote(String versionNote) {
        this.versionNote = versionNote;
    }

    public Instant getDisabledAt() {
        return disabledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
