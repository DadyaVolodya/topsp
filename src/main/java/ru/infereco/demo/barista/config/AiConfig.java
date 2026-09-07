package ru.infereco.demo.barista.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder, MeetProperties properties) {
        return builder.defaultSystem(properties.fullSystemPrompt()).build();
    }
}
