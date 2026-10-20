package com.lebuzzer.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

import com.lebuzzer.backend.dto.GameMessageMetrics;
import com.lebuzzer.backend.service.MessageMetrics.Direction;

class MessageMetricsTest {

	private final MessageMetrics metrics = new MessageMetrics();

	@Test
	void framesWithoutTheCode_areCountedForTheGameOfTheirSession() {
		record(Direction.CLIENT_TO_SERVER, "s1", SimpMessageType.CONNECT, null, Map.of());
		record(Direction.SERVER_TO_CLIENT, "s1", SimpMessageType.CONNECT_ACK, null, Map.of());
		record(Direction.CLIENT_TO_SERVER, "s1", SimpMessageType.SUBSCRIBE, "/topic/game/ABCDE", Map.of());
		record(Direction.SERVER_TO_CLIENT, "s1", SimpMessageType.MESSAGE, "/queue/state-users1",
				Map.of(SimpMessageHeaderAccessor.ORIGINAL_DESTINATION, "/user/queue/state"));
		// What Spring really delivers: the internal user destination, without the original one.
		record(Direction.SERVER_TO_CLIENT, "s1", SimpMessageType.MESSAGE, "/queue/errors-users1", Map.of());
		record(Direction.CLIENT_TO_SERVER, "s1", SimpMessageType.HEARTBEAT, null, Map.of());

		GameMessageMetrics game = metrics.forGame("ABCDE").orElseThrow();
		assertEquals(6, game.total());
		assertEquals(1, game.connections());
		assertEquals(1L, game.clientToServer().byType().get("CONNECT"), "frames sent before the code was known count too");
		assertEquals(1L, game.serverToClient().byType().get("MESSAGE snapshot"));
		assertEquals(1L, game.serverToClient().byType().get("MESSAGE error"));
		assertEquals(1, game.heartbeats());
	}

	@Test
	void sendsAndRoomEvents_areLabelledByActionAndType() {
		record(Direction.CLIENT_TO_SERVER, "s1", SimpMessageType.MESSAGE, "/app/game/ABCDE/answer", Map.of());
		Message<byte[]> event = MessageBuilder.withPayload(new byte[0])
				.setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, SimpMessageType.MESSAGE)
				.setHeader(SimpMessageHeaderAccessor.SESSION_ID_HEADER, "s2")
				.setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, "/topic/game/ABCDE")
				.setHeader(SimpMessageHeaderAccessor.NATIVE_HEADERS,
						Map.of(StompGameNotifier.EVENT_TYPE_HEADER, List.of("QUESTION_STARTED")))
				.build();
		metrics.record(Direction.SERVER_TO_CLIENT, event);

		GameMessageMetrics game = metrics.forGame("ABCDE").orElseThrow();
		assertEquals(1L, game.clientToServer().byType().get("SEND answer"));
		assertEquals(1L, game.serverToClient().byType().get("MESSAGE QUESTION_STARTED"));
		assertEquals(2, game.applicationMessages());
		assertEquals(2, game.connections());
	}

	@Test
	void twoGames_areCountedSeparately_andAConnectionWithoutGameIsInNone() {
		record(Direction.CLIENT_TO_SERVER, "a", SimpMessageType.SUBSCRIBE, "/topic/game/AAAAA", Map.of());
		record(Direction.CLIENT_TO_SERVER, "b", SimpMessageType.SUBSCRIBE, "/topic/game/BBBBB", Map.of());
		record(Direction.CLIENT_TO_SERVER, "b", SimpMessageType.HEARTBEAT, null, Map.of());
		record(Direction.CLIENT_TO_SERVER, "creator", SimpMessageType.MESSAGE, "/app/game/create", Map.of());

		assertEquals(1, metrics.forGame("AAAAA").orElseThrow().total());
		assertEquals(2, metrics.forGame("BBBBB").orElseThrow().total());
		assertTrue(metrics.forGame("ZZZZZ").isEmpty());
	}

	private void record(Direction direction, String sessionId, SimpMessageType type, String destination,
			Map<String, Object> extraHeaders) {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(type);
		headers.setSessionId(sessionId);
		if (destination != null) {
			headers.setDestination(destination);
		}
		extraHeaders.forEach(headers::setHeader);
		metrics.record(direction, MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()));
	}

}
