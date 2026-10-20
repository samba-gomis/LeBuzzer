package com.lebuzzer.backend.service;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.stereotype.Component;

import com.lebuzzer.backend.dto.DirectionCounts;
import com.lebuzzer.backend.dto.GameMessageMetrics;

/**
 * Counts every STOMP message that crosses the server, per game and per type: what really goes over
 * the WebSockets, heartbeats and protocol frames included (used for the polling vs WebSocket note).
 *
 * Which game a message belongs to:
 * <ul>
 * <li>by its destination when it contains the code (/app/game/{code}/..., /topic/game/{code});</li>
 * <li>otherwise (private /user/queue/... messages, heartbeats, CONNECT...) through its WebSocket session,
 * which learns its game from its first message that carries the code. Everything the session exchanged
 * before that moment is counted too.</li>
 * </ul>
 * A connection that never names a game (the short one used to create a game) is not counted in any game.
 */
@Component
public class MessageMetrics {

	public enum Direction {
		CLIENT_TO_SERVER,
		SERVER_TO_CLIENT
	}

	private static final Logger log = LoggerFactory.getLogger(MessageMetrics.class);
	private static final Pattern GAME_DESTINATION = Pattern.compile("^/(?:app|topic)/game/([A-Z]{5})(?:/.*)?$");

	/** By WebSocket session id. Kept after the disconnection, so that the counts can still be read. */
	private final Map<String, SessionCounts> sessions = new ConcurrentHashMap<>();

	/** Called by MessageCounterInterceptor for every message on the client inbound and outbound channels. */
	public void record(Direction direction, Message<?> message) {
		Map<String, Object> headers = message.getHeaders();
		String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
		if (sessionId == null) {
			return;
		}
		String destination = SimpMessageHeaderAccessor.getDestination(headers);
		SessionCounts session = sessions.computeIfAbsent(sessionId, id -> new SessionCounts());
		session.attachToGame(gameCodeIn(destination));
		session.increment(direction, label(direction, headers, destination));
	}

	public Optional<GameMessageMetrics> forGame(String code) {
		Map<String, Long> clientToServer = new TreeMap<>();
		Map<String, Long> serverToClient = new TreeMap<>();
		int connections = 0;
		for (SessionCounts session : sessions.values()) {
			if (code.equals(session.gameCode)) {
				connections++;
				session.clientToServer.forEach((type, count) -> clientToServer.merge(type, count.sum(), Long::sum));
				session.serverToClient.forEach((type, count) -> serverToClient.merge(type, count.sum(), Long::sum));
			}
		}
		if (connections == 0) {
			return Optional.empty();
		}
		long totalIn = sum(clientToServer);
		long totalOut = sum(serverToClient);
		long heartbeats = clientToServer.getOrDefault("HEARTBEAT", 0L) + serverToClient.getOrDefault("HEARTBEAT", 0L);
		long application = sumStartingWith(clientToServer, "SEND ") + sumStartingWith(serverToClient, "MESSAGE ");
		return Optional.of(new GameMessageMetrics(code, connections, totalIn + totalOut, application, heartbeats,
				totalIn + totalOut - application - heartbeats, new DirectionCounts(totalIn, clientToServer),
				new DirectionCounts(totalOut, serverToClient)));
	}

	public void logSummary(String code) {
		forGame(code).ifPresent(metrics -> log.info(
				"Game {}: {} STOMP messages over {} connections ({} application, {} heartbeats, {} protocol)."
						+ " Client to server: {}. Server to client: {}.",
				code, metrics.total(), metrics.connections(), metrics.applicationMessages(), metrics.heartbeats(),
				metrics.protocolFrames(), metrics.clientToServer().byType(), metrics.serverToClient().byType()));
	}

	// ------------------------------------------------------------------ labels

	static String gameCodeIn(String destination) {
		if (destination == null) {
			return null;
		}
		Matcher matcher = GAME_DESTINATION.matcher(destination);
		return matcher.matches() ? matcher.group(1) : null;
	}

	/** "SEND answer", "MESSAGE QUESTION_STARTED", "MESSAGE snapshot", "HEARTBEAT", "SUBSCRIBE"... */
	static String label(Direction direction, Map<String, Object> headers, String destination) {
		SimpMessageType type = SimpMessageHeaderAccessor.getMessageType(headers);
		if (type == null) {
			return "OTHER";
		}
		return switch (type) {
			case MESSAGE -> direction == Direction.CLIENT_TO_SERVER
					? "SEND " + lastSegment(destination)
					: "MESSAGE " + outgoingKind(headers, destination);
			case CONNECT_ACK -> "CONNECTED";
			default -> type.name();
		};
	}

	private static String outgoingKind(Map<String, Object> headers, String destination) {
		// Room events carry their type in a STOMP header set by StompGameNotifier.
		String eventType = SimpMessageHeaderAccessor.getFirstNativeHeader(StompGameNotifier.EVENT_TYPE_HEADER, headers);
		if (eventType != null) {
			return eventType;
		}
		// Private messages: Spring delivers /user/queue/state to the internal destination
		// /queue/state-user{sessionId}; both forms are brought back to /queue/state.
		String target = headers.get(SimpMessageHeaderAccessor.ORIGINAL_DESTINATION) instanceof String original
				? original
				: destination;
		String queue = target == null ? "" : target.replaceFirst("^/user", "").replaceFirst("-user[^/]*$", "");
		return switch (queue) {
			case "/queue/state" -> "snapshot";
			case "/queue/errors" -> "error";
			case "/queue/game" -> "game-created";
			default -> queue;
		};
	}

	private static String lastSegment(String destination) {
		return destination == null ? "?" : destination.substring(destination.lastIndexOf('/') + 1);
	}

	private static long sum(Map<String, Long> counts) {
		return counts.values().stream().mapToLong(Long::longValue).sum();
	}

	private static long sumStartingWith(Map<String, Long> counts, String prefix) {
		return counts.entrySet().stream()
				.filter(entry -> entry.getKey().startsWith(prefix))
				.mapToLong(Map.Entry::getValue)
				.sum();
	}

	/** Counts of one WebSocket session. Updated from several threads: concurrent maps and LongAdder. */
	private static final class SessionCounts {

		private volatile String gameCode;
		private final Map<String, LongAdder> clientToServer = new ConcurrentHashMap<>();
		private final Map<String, LongAdder> serverToClient = new ConcurrentHashMap<>();

		void attachToGame(String code) {
			if (code != null && gameCode == null) {
				gameCode = code;
			}
		}

		void increment(Direction direction, String label) {
			Map<String, LongAdder> counts = direction == Direction.CLIENT_TO_SERVER ? clientToServer : serverToClient;
			counts.computeIfAbsent(label, key -> new LongAdder()).increment();
		}

	}

}
