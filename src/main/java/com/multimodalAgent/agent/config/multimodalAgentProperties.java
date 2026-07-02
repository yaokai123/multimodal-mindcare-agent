package com.multimodalAgent.agent.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "multimodal-agent")
/**
 * multimodal-agent.* 配置映射。
 *
 * <p>所有业务配置集中在这里，便于通过 application.yml 或环境变量切换模型、
 * RAG、知识库切块、Excel 写入和邮件预警行为。</p>
 */
public class multimodalAgentProperties {

    private final Ai ai = new Ai();
    private final Chat chat = new Chat();
    private final Embedding embedding = new Embedding();
    private final Knowledge knowledge = new Knowledge();
    private final Multimodal multimodal = new Multimodal();
    private final Voice voice = new Voice();
    private final Mcp mcp = new Mcp();
    private final Security security = new Security();
    private final Http http = new Http();
    private final Upload upload = new Upload();

    public Ai getAi() {
        return ai;
    }

    public Chat getChat() {
        return chat;
    }

    public Embedding getEmbedding() {
        return embedding;
    }

    public Knowledge getKnowledge() {
        return knowledge;
    }

    public Multimodal getMultimodal() {
        return multimodal;
    }

    public Voice getVoice() {
        return voice;
    }

    public Mcp getMcp() {
        return mcp;
    }

    public Security getSecurity() {
        return security;
    }

    public Http getHttp() {
        return http;
    }

    public Upload getUpload() {
        return upload;
    }

    public static class Ai {
        /** 模型提供方：ollama、openai 或 mock。 */
        private String provider = "ollama";
        /** 生成温度，值越高回答越发散。 */
        private double temperature = 0.35;
        /** 学生端单次回复的最大生成 token 数，避免本地模型无边界扩写。 */
        private int maxTokens = 320;
        private final Ollama ollama = new Ollama();
        private final OpenAi openai = new OpenAi();

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        public Ollama getOllama() {
            return ollama;
        }

        public OpenAi getOpenai() {
            return openai;
        }
    }

    public static class Ollama {
        /** 本地模型服务地址。 */
        private String baseUrl = "http://localhost:11434";
        /** multimodalAgent 项目模型名称。 */
        private String model = "multimodalAgent-qwen2.5-7b-ft:latest";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class OpenAi {
        /** OpenAI 兼容接口地址。 */
        private String baseUrl = "https://api.openai.com";
        /** OpenAI API Key，未配置时不能启用 openai provider。 */
        private String apiKey = "";
        /** OpenAI 聊天模型名称。 */
        private String model = "gpt-4o-mini";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Chat {
        /** 保留给模型的历史轮次数，服务层会换算成用户/助手消息条数。 */
        private int historyLimit = 6;
        /** Redis 短期记忆 TTL，过期后可从 MySQL 长期记忆恢复最近上下文。 */
        private long shortMemoryTtlHours = 24;

        public int getHistoryLimit() {
            return historyLimit;
        }

        public void setHistoryLimit(int historyLimit) {
            this.historyLimit = historyLimit;
        }

        public long getShortMemoryTtlHours() {
            return shortMemoryTtlHours;
        }

        public void setShortMemoryTtlHours(long shortMemoryTtlHours) {
            this.shortMemoryTtlHours = shortMemoryTtlHours;
        }
    }

    public static class Embedding {
        /** Embedding 服务地址。 */
        private String baseUrl = "https://api.openai.com";
        /** Embedding API Key，留空时自动走本地检索兜底。 */
        private String apiKey = "";
        /** 文档要求的默认 embedding 模型。 */
        private String model = "text-embedding-3-small";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }

    public static class Knowledge {
        /** 每次 RAG 检索返回的候选片段数量。 */
        private int topK = 3;
        private boolean fastMode = true;
        private boolean plannerEnabled;
        private boolean reviewEnabled;
        private int maxQueries = 1;
        /** 是否启用外部 Chroma 向量库。 */
        private boolean useChroma;
        private String chromaBaseUrl = "http://localhost:8000";
        private String chromaCollection = "multimodalAgent_knowledge";
        private int chunkSize = 512;
        private int chunkOverlap = 64;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public boolean isFastMode() {
            return fastMode;
        }

        public void setFastMode(boolean fastMode) {
            this.fastMode = fastMode;
        }

        public boolean isPlannerEnabled() {
            return plannerEnabled;
        }

        public void setPlannerEnabled(boolean plannerEnabled) {
            this.plannerEnabled = plannerEnabled;
        }

        public boolean isReviewEnabled() {
            return reviewEnabled;
        }

        public void setReviewEnabled(boolean reviewEnabled) {
            this.reviewEnabled = reviewEnabled;
        }

        public int getMaxQueries() {
            return maxQueries;
        }

        public void setMaxQueries(int maxQueries) {
            this.maxQueries = maxQueries;
        }

        public boolean isUseChroma() {
            return useChroma;
        }

        public void setUseChroma(boolean useChroma) {
            this.useChroma = useChroma;
        }

        public String getChromaBaseUrl() {
            return chromaBaseUrl;
        }

        public void setChromaBaseUrl(String chromaBaseUrl) {
            this.chromaBaseUrl = chromaBaseUrl;
        }

        public String getChromaCollection() {
            return chromaCollection;
        }

        public void setChromaCollection(String chromaCollection) {
            this.chromaCollection = chromaCollection;
        }

        public int getChunkSize() {
            return chunkSize;
        }

        public void setChunkSize(int chunkSize) {
            this.chunkSize = chunkSize;
        }

        public int getChunkOverlap() {
            return chunkOverlap;
        }

        public void setChunkOverlap(int chunkOverlap) {
            this.chunkOverlap = chunkOverlap;
        }
    }

    public static class Multimodal {
        private final Whisper whisper = new Whisper();
        private final MediaPipe mediaPipe = new MediaPipe();
        /** 文档中的融合权重：文本 10%、语音 40%、视觉 50%，缺失模态按 0 分处理。 */
        private double textWeight = 0.1;
        private double audioWeight = 0.4;
        private double visualWeight = 0.5;

        public Whisper getWhisper() {
            return whisper;
        }

        public MediaPipe getMediaPipe() {
            return mediaPipe;
        }

        public double getTextWeight() {
            return textWeight;
        }

        public void setTextWeight(double textWeight) {
            this.textWeight = textWeight;
        }

        public double getAudioWeight() {
            return audioWeight;
        }

        public void setAudioWeight(double audioWeight) {
            this.audioWeight = audioWeight;
        }

        public double getVisualWeight() {
            return visualWeight;
        }

        public void setVisualWeight(double visualWeight) {
            this.visualWeight = visualWeight;
        }
    }

    public static class Whisper {
        /** Whisper 接入模式：mock 或 openai。 */
        private String mode = "mock";
        private String baseUrl = "https://api.openai.com";
        private String apiKey = "";
        private String model = "whisper-1";
        private boolean demoFilenameSignals;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public boolean isDemoFilenameSignals() {
            return demoFilenameSignals;
        }

        public void setDemoFilenameSignals(boolean demoFilenameSignals) {
            this.demoFilenameSignals = demoFilenameSignals;
        }
    }

    public static class MediaPipe {
        /** MediaPipe 接入模式：local-rule 或 http。 */
        private String mode = "local-rule";
        private String url = "http://localhost:8090/analyze";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }

    public static class Voice {
        private boolean enabled;
        private final LiveKit livekit = new LiveKit();
        private final RealtimeAsr asr = new RealtimeAsr();
        private final RealtimeTts tts = new RealtimeTts();
        private boolean interruptEnabled = true;
        private String sessionMode = "assistive";
        private String soothingVoice = "zh_female_vv_uranus_bigtts";
        private String conciseVoice = "zh_female_xiaohe_uranus_bigtts";
        private String formalAlertVoice = "zh_male_m191_uranus_bigtts";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public LiveKit getLivekit() {
            return livekit;
        }

        public RealtimeAsr getAsr() {
            return asr;
        }

        public RealtimeTts getTts() {
            return tts;
        }

        public boolean isInterruptEnabled() {
            return interruptEnabled;
        }

        public void setInterruptEnabled(boolean interruptEnabled) {
            this.interruptEnabled = interruptEnabled;
        }

        public String getSessionMode() {
            return sessionMode;
        }

        public void setSessionMode(String sessionMode) {
            this.sessionMode = sessionMode;
        }

        public String getSoothingVoice() {
            return soothingVoice;
        }

        public void setSoothingVoice(String soothingVoice) {
            this.soothingVoice = soothingVoice;
        }

        public String getConciseVoice() {
            return conciseVoice;
        }

        public void setConciseVoice(String conciseVoice) {
            this.conciseVoice = conciseVoice;
        }

        public String getFormalAlertVoice() {
            return formalAlertVoice;
        }

        public void setFormalAlertVoice(String formalAlertVoice) {
            this.formalAlertVoice = formalAlertVoice;
        }
    }

    public static class LiveKit {
        private String url = "";
        private String apiKey = "";
        private String apiSecret = "";
        private int tokenTtlMinutes = 30;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getApiSecret() {
            return apiSecret;
        }

        public void setApiSecret(String apiSecret) {
            this.apiSecret = apiSecret;
        }

        public int getTokenTtlMinutes() {
            return tokenTtlMinutes;
        }

        public void setTokenTtlMinutes(int tokenTtlMinutes) {
            this.tokenTtlMinutes = tokenTtlMinutes;
        }
    }

    public static class RealtimeAsr {
        private String provider = "doubao";
        private String model = "doubao-streaming-asr";
        private String endpoint = "";
        private String apiKey = "";
        private String appId = "";
        private String cluster = "";
        private String format = "pcm";
        private int sampleRate = 16000;
        private String language = "zh-CN";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getCluster() {
            return cluster;
        }

        public void setCluster(String cluster) {
            this.cluster = cluster;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }

    public static class RealtimeTts {
        private String provider = "doubao";
        private String model = "seed-tts-2.0";
        private String voice = "zh_female_vv_uranus_bigtts";
        private String endpoint = "";
        private String apiKey = "";
        private String appId = "";
        private String appKey = "";
        private String groupId = "";
        private String resourceId = "";
        private String audioFormat = "mp3";
        private int sampleRate = 32000;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getVoice() {
            return voice;
        }

        public void setVoice(String voice) {
            this.voice = voice;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppKey() {
            return appKey;
        }

        public void setAppKey(String appKey) {
            this.appKey = appKey;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getResourceId() {
            return resourceId;
        }

        public void setResourceId(String resourceId) {
            this.resourceId = resourceId;
        }

        public String getAudioFormat() {
            return audioFormat;
        }

        public void setAudioFormat(String audioFormat) {
            this.audioFormat = audioFormat;
        }

        public int getSampleRate() {
            return sampleRate;
        }

        public void setSampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
        }
    }

    public static class Mcp {
        private final Excel excel = new Excel();
        private final Email email = new Email();
        private final Server server = new Server();

        public Excel getExcel() {
            return excel;
        }

        public Email getEmail() {
            return email;
        }

        public Server getServer() {
            return server;
        }
    }

    public static class Excel {
        /** Excel 写入模式：mcp、local 或 http。 */
        private String mode = "local";
        private String url = "http://localhost:8080/mcp";
        private String localPath = "./data/multimodalAgent-reports.xlsx";

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getLocalPath() {
            return localPath;
        }

        public void setLocalPath(String localPath) {
            this.localPath = localPath;
        }
    }

    public static class Email {
        /** 邮件预警模式：mcp、log、smtp 或 http。 */
        private String mode = "log";
        private String url = "http://localhost:8080/mcp";
        private String from = "multimodalAgent@example.com";
        private List<String> recipients = new ArrayList<>(List.of("counselor@example.com"));
        private int maxRetries = 2;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public List<String> getRecipients() {
            return recipients;
        }

        public void setRecipients(List<String> recipients) {
            this.recipients = recipients;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }
    }

    public static class Server {
        private boolean enabled;
        private String authToken = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getAuthToken() {
            return authToken;
        }

        public void setAuthToken(String authToken) {
            this.authToken = authToken;
        }
    }

    public static class Security {
        private boolean seedDemoUsers;
        private String adminPassword = "";
        private String studentPassword = "";

        public boolean isSeedDemoUsers() {
            return seedDemoUsers;
        }

        public void setSeedDemoUsers(boolean seedDemoUsers) {
            this.seedDemoUsers = seedDemoUsers;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }

        public String getStudentPassword() {
            return studentPassword;
        }

        public void setStudentPassword(String studentPassword) {
            this.studentPassword = studentPassword;
        }
    }

    public static class Http {
        private int connectTimeoutMillis = 3000;
        private int responseTimeoutSeconds = 30;

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getResponseTimeoutSeconds() {
            return responseTimeoutSeconds;
        }

        public void setResponseTimeoutSeconds(int responseTimeoutSeconds) {
            this.responseTimeoutSeconds = responseTimeoutSeconds;
        }
    }

    public static class Upload {
        private long knowledgeMaxBytes = 2 * 1024 * 1024;
        private long multimodalMaxBytes = 8 * 1024 * 1024;

        public long getKnowledgeMaxBytes() {
            return knowledgeMaxBytes;
        }

        public void setKnowledgeMaxBytes(long knowledgeMaxBytes) {
            this.knowledgeMaxBytes = knowledgeMaxBytes;
        }

        public long getMultimodalMaxBytes() {
            return multimodalMaxBytes;
        }

        public void setMultimodalMaxBytes(long multimodalMaxBytes) {
            this.multimodalMaxBytes = multimodalMaxBytes;
        }
    }
}
