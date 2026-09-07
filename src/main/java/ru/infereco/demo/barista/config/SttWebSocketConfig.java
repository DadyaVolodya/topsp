package ru.infereco.demo.barista.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import ru.infereco.demo.barista.stt.SttWebSocketHandler;

@Configuration
@EnableWebSocket
public class SttWebSocketConfig implements WebSocketConfigurer {

    private final SttWebSocketHandler handler;

    public SttWebSocketConfig(SttWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/stt").setAllowedOrigins("*");
    }

    @Bean
    public ServletServerContainerFactoryBean sttWsContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(64 * 1024);
        container.setMaxTextMessageBufferSize(16 * 1024);
        return container;
    }
}
