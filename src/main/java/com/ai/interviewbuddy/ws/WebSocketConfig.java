package com.ai.interviewbuddy.ws;

import com.ai.interviewbuddy.ws.AudioWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(audioWebSocketHandler(), "/ws/audio")
                .setAllowedOrigins("*");
    }

    @Bean
    public WebSocketHandler audioWebSocketHandler() {
        return new AudioWebSocketHandler();
    }
}
