package com.lebuzzer.backend.config;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

/**
 * There are no user accounts, so nobody is authenticated. Without a {@link Principal},
 * Spring cannot route {@code /user/queue/...} messages to a single connection.
 * This handler gives each WebSocket connection a random, anonymous identity at handshake time.
 */
public class AnonymousPrincipalHandshakeHandler extends DefaultHandshakeHandler {

	@Override
	protected Principal determineUser(ServerHttpRequest request, WebSocketHandler wsHandler,
			Map<String, Object> attributes) {
		return new AnonymousPrincipal(UUID.randomUUID().toString());
	}

	private record AnonymousPrincipal(String name) implements Principal {

		@Override
		public String getName() {
			return name;
		}

	}

}
