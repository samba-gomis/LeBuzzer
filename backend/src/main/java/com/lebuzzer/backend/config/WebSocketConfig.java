package com.lebuzzer.backend.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.lebuzzer.backend.service.MessageMetrics;

/**
 * STOMP over WebSocket, with Spring's in-memory "simple" broker.
 *
 * <ul>
 * <li>client to server: {@code /app/...} (handled by {@code @MessageMapping} methods)</li>
 * <li>server to a room: {@code /topic/...}</li>
 * <li>server to one connection: {@code /user/queue/...}</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

	/** Heartbeats every 10 s in both directions: a dead connection is detected in seconds. */
	private static final long HEARTBEAT_INTERVAL_MS = 10_000;

	/**
	 * The simple broker only sends heartbeats if it is given a scheduler. We reuse the one
	 * Spring already creates for the broker. {@code @Lazy} is needed because that scheduler
	 * is itself created by the configuration that calls this class.
	 */
	private final TaskScheduler messageBrokerTaskScheduler;
	private final MessageMetrics messageMetrics;

	public WebSocketConfig(@Lazy @Qualifier("messageBrokerTaskScheduler") TaskScheduler messageBrokerTaskScheduler,
			MessageMetrics messageMetrics) {
		this.messageBrokerTaskScheduler = messageBrokerTaskScheduler;
		this.messageMetrics = messageMetrics;
	}

	@Override
	public void registerStompEndpoints(StompEndpointRegistry registry) {
		registry.addEndpoint("/ws")
				// Gives every connection its own anonymous identity (needed for /user/queue/...).
				.setHandshakeHandler(new AnonymousPrincipalHandshakeHandler())
				// The Vite dev server, opened on this PC or from a phone on the same local network.
				.setAllowedOriginPatterns(
						"http://localhost:5173",
						"http://127.0.0.1:5173",
						"http://192.168.*:5173",
						"http://10.*:5173",
						"http://172.*:5173");
	}

	@Override
	public void configureMessageBroker(MessageBrokerRegistry registry) {
		registry.enableSimpleBroker("/topic", "/queue")
				.setHeartbeatValue(new long[] { HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS })
				.setTaskScheduler(messageBrokerTaskScheduler);
		registry.setApplicationDestinationPrefixes("/app");
		registry.setUserDestinationPrefix("/user");
		// Messages sent to one client keep the order in which the server published them
		// (e.g. a state snapshot is never overtaken by an older event).
		registry.setPreservePublishOrder(true);
	}

	/** Every frame received from a client (SEND, SUBSCRIBE, heartbeats...) is counted. */
	@Override
	public void configureClientInboundChannel(ChannelRegistration registration) {
		registration.interceptors(new MessageCounterInterceptor(messageMetrics, MessageMetrics.Direction.CLIENT_TO_SERVER));
	}

	/** Every frame sent to a client (one per subscriber for a broadcast, heartbeats...) is counted. */
	@Override
	public void configureClientOutboundChannel(ChannelRegistration registration) {
		registration.interceptors(new MessageCounterInterceptor(messageMetrics, MessageMetrics.Direction.SERVER_TO_CLIENT));
	}

}
