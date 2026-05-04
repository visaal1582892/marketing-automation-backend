package com.medplus.marketing_automation_backend.config;

import com.medplus.marketing_automation_backend.security.JwtChannelInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    public WebSocketConfig(JwtChannelInterceptor jwtChannelInterceptor) {
        this.jwtChannelInterceptor = jwtChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory simple broker for /topic destinations
        registry.enableSimpleBroker("/topic");
        // Client sends messages to @MessageMapping methods via /app prefix
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket endpoint — frontend connects to ws://<host>/ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Validate JWT on every inbound STOMP frame (especially CONNECT)
        registration.interceptors(jwtChannelInterceptor);
    }
}
