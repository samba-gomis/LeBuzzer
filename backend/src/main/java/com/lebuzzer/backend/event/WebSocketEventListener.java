package com.lebuzzer.backend.event;

import java.security.Principal;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.lebuzzer.backend.service.GameService;

/**
 * Spring publishes a SessionDisconnectEvent when a WebSocket connection closes: tab closed,
 * network lost, or missing heartbeats. The game itself goes on; only that participant is affected.
 */
@Component
public class WebSocketEventListener {

	private final GameService gameService;

	public WebSocketEventListener(GameService gameService) {
		this.gameService = gameService;
	}

	@EventListener
	public void onDisconnect(SessionDisconnectEvent event) {
		Principal user = event.getUser();
		if (user != null) {
			gameService.handleDisconnect(user.getName());
		}
	}

}
