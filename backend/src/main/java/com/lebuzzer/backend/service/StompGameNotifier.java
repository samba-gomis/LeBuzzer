package com.lebuzzer.backend.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.lebuzzer.backend.dto.GameCreated;
import com.lebuzzer.backend.dto.GameSnapshot;
import com.lebuzzer.backend.dto.RoomEvent;

/**
 * The only place that builds STOMP destinations. The room of a game is
 * /topic/game/{code}: a message for game A is published on A's topic only, so it can
 * never reach the subscribers of game B.
 */
@Component
public class StompGameNotifier implements GameNotifier {

	private static final String ROOM_PREFIX = "/topic/game/";

	private final SimpMessagingTemplate messagingTemplate;

	public StompGameNotifier(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void broadcast(String gameCode, RoomEvent event) {
		messagingTemplate.convertAndSend(ROOM_PREFIX + gameCode, event);
	}

	@Override
	public void sendGameCreated(String connectionId, GameCreated message) {
		// The client subscribes to /user/queue/game; Spring routes it to this connection only.
		messagingTemplate.convertAndSendToUser(connectionId, "/queue/game", message);
	}

	@Override
	public void sendSnapshot(String connectionId, GameSnapshot snapshot) {
		messagingTemplate.convertAndSendToUser(connectionId, "/queue/state", snapshot);
	}

}
