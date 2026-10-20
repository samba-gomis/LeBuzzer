package com.lebuzzer.backend.service;

import java.util.Map;

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

	/** STOMP header carrying the type of a room event; read by MessageMetrics to count messages per type. */
	public static final String EVENT_TYPE_HEADER = "event-type";

	private static final String ROOM_PREFIX = "/topic/game/";

	private final SimpMessagingTemplate messagingTemplate;

	public StompGameNotifier(SimpMessagingTemplate messagingTemplate) {
		this.messagingTemplate = messagingTemplate;
	}

	@Override
	public void broadcast(String gameCode, RoomEvent event) {
		messagingTemplate.convertAndSend(ROOM_PREFIX + gameCode, event,
				Map.<String, Object>of(EVENT_TYPE_HEADER, event.type().name()));
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

	@Override
	public void sendAnswerProgress(String hostConnectionId, RoomEvent.AnswerProgress progress) {
		messagingTemplate.convertAndSendToUser(hostConnectionId, "/queue/progress", progress,
				Map.<String, Object>of(EVENT_TYPE_HEADER, progress.type().name()));
	}

}
