package com.lebuzzer.backend.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;

class StompDestinationGuardTest {

	private final StompDestinationGuard guard = new StompDestinationGuard();

	@ParameterizedTest
	@ValueSource(strings = { "/topic/game/ABCDE", "/user/queue/game", "/user/queue/state", "/user/queue/errors",
			"/user/queue/progress" })
	void theSubscriptionsOfTheGame_areAllowed(String destination) {
		Message<byte[]> message = frame(SimpMessageType.SUBSCRIBE, destination);

		assertSame(message, guard.preSend(message, null), "the message goes on unchanged");
	}

	@ParameterizedTest
	@ValueSource(strings = { "/queue/**", "/queue/*", "/topic/**", "/topic/*", "/topic/game/*", "/topic/game/**",
			"/topic/game/ABC*", "/topic/game/abcde", "/topic/game/ABCDE/x", "/topic/pong", "/queue/state-usersomeone",
			"/user/someone/queue/state", "/user/queue/**", "/user/queue/other", "/**", "" })
	void anyOtherSubscription_isRefused(String destination) {
		Message<byte[]> message = frame(SimpMessageType.SUBSCRIBE, destination);

		assertThrows(MessageDeliveryException.class, () -> guard.preSend(message, null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/app/game/create", "/app/game/ABCDE/join", "/app/game/ABCDE/answer" })
	void sendingToTheApplication_isAllowed(String destination) {
		assertDoesNotThrow(() -> guard.preSend(frame(SimpMessageType.MESSAGE, destination), null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "/topic/game/ABCDE", "/queue/state-usersomeone", "/user/someone/queue/state", "/app", "" })
	void sendingAnywhereElse_isRefused(String destination) {
		Message<byte[]> message = frame(SimpMessageType.MESSAGE, destination);

		assertThrows(MessageDeliveryException.class, () -> guard.preSend(message, null));
	}

	@Test
	void aSubscriptionOrASendWithoutDestination_isRefused() {
		assertThrows(MessageDeliveryException.class, () -> guard.preSend(frame(SimpMessageType.SUBSCRIBE, null), null));
		assertThrows(MessageDeliveryException.class, () -> guard.preSend(frame(SimpMessageType.MESSAGE, null), null));
	}

	@ParameterizedTest
	@ValueSource(strings = { "CONNECT", "HEARTBEAT", "UNSUBSCRIBE", "DISCONNECT" })
	void framesWithoutDestination_goThrough(String type) {
		assertDoesNotThrow(() -> guard.preSend(frame(SimpMessageType.valueOf(type), null), null));
	}

	private static Message<byte[]> frame(SimpMessageType type, String destination) {
		SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(type);
		if (destination != null) {
			headers.setDestination(destination);
		}
		return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
	}

}
