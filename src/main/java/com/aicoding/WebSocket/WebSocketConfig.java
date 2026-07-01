package com.aicoding.WebSocket;

import com.aicoding.Entity.model.CustomOAuth2User;
import com.aicoding.Service.ProjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String PROJECT_TOPIC_PREFIX = "/topic/project.";

    private final ProjectService projectService;

    @Value("#{'${app.cors.allowed-origins:http://localhost:3000,http://localhost:3001,http://127.0.0.1:3000}'.split(',')}")
    private List<String> allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic");
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.stream().map(String::trim).toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                authorizeProjectSubscription(StompHeaderAccessor.wrap(message));
                return message;
            }
        });
    }

    private void authorizeProjectSubscription(StompHeaderAccessor headers) {
        if (headers.getCommand() != StompCommand.SUBSCRIBE) {
            return;
        }

        String destination = headers.getDestination();
        if (destination == null || !destination.startsWith(PROJECT_TOPIC_PREFIX)) {
            throw new AccessDeniedException("Unsupported WebSocket subscription");
        }

        CustomOAuth2User user = authenticatedUser(headers);
        try {
            Long projectId = Long.valueOf(destination.substring(PROJECT_TOPIC_PREFIX.length()));
            projectService.getProjectByIdAndUserId(projectId, user.getId());
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("Invalid project subscription", e);
        }
    }

    private CustomOAuth2User authenticatedUser(StompHeaderAccessor headers) {
        if (headers.getUser() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof CustomOAuth2User user) {
            return user;
        }
        throw new AccessDeniedException("Authenticated WebSocket session required");
    }
}
