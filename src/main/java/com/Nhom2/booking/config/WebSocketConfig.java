package com.Nhom2.booking.config;

import com.Nhom2.booking.security.StompAuthChannelInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over plain WebSocket at /ws for pushing support-chat messages. Note that Vercel rewrites cannot proxy
 * WebSockets, so the deployed frontend connects to the backend URL directly (VITE_WS_URL).
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * STOMP heartbeats in both directions. Without them a quiet chat sends nothing, and the proxy in front of
     * Render (Cloudflare) drops WebSockets that stay idle for about 100 seconds, losing pushes until the client
     * reconnects.
     */
    static final long HEARTBEAT_MS = 10_000;

    private final StompAuthChannelInterceptor authInterceptor;
    private final TaskScheduler heartbeatScheduler;

    // @Lazy: messageBrokerTaskScheduler is defined by the broker configuration that this class configures.
    public WebSocketConfig(
            StompAuthChannelInterceptor authInterceptor,
            @Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler heartbeatScheduler
    ) {
        this.authInterceptor = authInterceptor;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(AllowedOrigins.PATTERNS.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MS, HEARTBEAT_MS})
                .setTaskScheduler(heartbeatScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authInterceptor);
    }
}
