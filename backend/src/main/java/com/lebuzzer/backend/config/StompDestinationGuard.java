package com.lebuzzer.backend.config;

import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;

/**
 * Checks every frame coming from a client: our frontend is not the only possible client, anyone can
 * open a WebSocket and send what they want. Without this guard, the simple broker accepts wildcard
 * subscriptions ({@code /queue/**} reads the private messages of everybody, hostToken and playerId
 * included) and direct sends to a room (fake events, bypassing all the rules of GameService).
 *
 * A refused frame raises an exception: Spring answers with a STOMP ERROR frame and closes the connection.
 */
public class StompDestinationGuard implements ChannelInterceptor {

	/** A client only talks to the application: the rules live in GameService. */
	private static final String SEND_PREFIX = "/app/";

	/** Exact destinations only, so a wildcard can never match. */
	private static final Pattern ALLOWED_SUBSCRIPTION = Pattern
			.compile("^(/topic/game/[A-Z]{5}|/user/queue/(game|state|errors|progress))$");

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(message.getHeaders());
		String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
		if (type == SimpMessageType.MESSAGE && !isAllowedSend(destination)) {
			throw new MessageDeliveryException(message, "Forbidden SEND destination: " + destination);
		}
		if (type == SimpMessageType.SUBSCRIBE && !isAllowedSubscription(destination)) {
			throw new MessageDeliveryException(message, "Forbidden SUBSCRIBE destination: " + destination);
		}
		return message;
	}

	private static boolean isAllowedSend(String destination) {
		return destination != null && destination.startsWith(SEND_PREFIX);
	}

	private static boolean isAllowedSubscription(String destination) {
		return destination != null && ALLOWED_SUBSCRIPTION.matcher(destination).matches();
	}

}
