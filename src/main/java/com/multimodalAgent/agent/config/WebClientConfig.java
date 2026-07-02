package com.multimodalAgent.agent.config;

import io.netty.channel.ChannelOption;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
/**
 * 共享 WebClient.Builder。
 *
 * <p>模型服务、embedding 服务和 HTTP MCP 工具都复用这个 Builder，便于统一扩展超时、
 * 日志或代理设置。</p>
 */
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder(multimodalAgentProperties properties) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getHttp().getConnectTimeoutMillis())
                .responseTimeout(Duration.ofSeconds(properties.getHttp().getResponseTimeoutSeconds()));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
