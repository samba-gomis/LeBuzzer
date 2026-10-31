package com.lebuzzer.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

/**
 * Plays against a real server with plain STOMP clients: what someone could do by bypassing our
 * frontend (a few lines of code are enough), and what a normal game needs to keep working.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StompSecurityIntegrationTest {

	private static final long TIMEOUT_SECONDS = 5;
	/** Leaves time for a frame to be handled by the server (client frames are handled in parallel). */
	private static final long SETTLE_MS = 300;

	@Value("${local.server.port}")
	private int port;

	private WebSocketStompClient stompClient;
	private final List<StompSession> sessions = new ArrayList<>();

	@BeforeEach
	void setUp() {
		stompClient = new WebSocketStompClient(new StandardWebSocketClient());
		stompClient.setMessageConverter(new JacksonJsonMessageConverter());
		stompClient.setDefaultHeartbeat(new long[] { 0, 0 });
	}

	@AfterEach
	void tearDown() {
		sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
		stompClient.stop();
	}

	@Test
	void aNormalGame_stillGoesThrough() throws Exception {
		Game game = createGame();
		StompSession player = connect();
		BlockingQueue<Map<?, ?>> room = subscribe(player, "/topic/game/" + game.code());
		BlockingQueue<Map<?, ?>> state = subscribe(player, "/user/queue/state");
		subscribe(player, "/user/queue/errors");
		settle();

		player.send("/app/game/" + game.code() + "/join", Map.of("nickname", "Samba"));

		assertNotNull(state.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the player receives the snapshot");
		assertNotNull(room.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS), "the player receives the room events");
		assertTrue(player.isConnected());
	}

	@Test
	void aWildcardSubscriber_readsNoSecretOfAnotherConnection() throws Exception {
		StompSession attacker = connect();
		BlockingQueue<Map<?, ?>> queues = subscribe(attacker, "/queue/**");
		BlockingQueue<Map<?, ?>> topics = subscribe(attacker, "/topic/**");
		settle();

		Game game = createGame();
		StompSession player = connect();
		BlockingQueue<Map<?, ?>> state = subscribe(player, "/user/queue/state");
		settle();
		player.send("/app/game/" + game.code() + "/join", Map.of("nickname", "Samba"));
		assertNotNull(state.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS));
		settle();

		assertTrue(queues.isEmpty(), "private messages leaked: " + queues);
		assertTrue(topics.isEmpty(), "room messages leaked: " + topics);
	}

	@ParameterizedTest
	@ValueSource(strings = { "/queue/**", "/topic/**", "/topic/game/*", "/queue/state-usersomeone",
			"/user/someone/queue/state", "/user/queue/**" })
	void aForbiddenSubscription_closesTheConnection(String destination) throws Exception {
		StompSession attacker = connect();

		subscribe(attacker, destination);

		assertClosed(attacker);
	}

	@Test
	void aClientCannotPublishDirectlyOnARoom() throws Exception {
		Game game = createGame();
		StompSession player = connect();
		BlockingQueue<Map<?, ?>> room = subscribe(player, "/topic/game/" + game.code());
		settle();
		StompSession attacker = connect();

		attacker.send("/topic/game/" + game.code(), Map.of("type", "QUESTION_CLOSED", "questionId", "fake"));

		assertClosed(attacker);
		assertNull(room.poll(SETTLE_MS, TimeUnit.MILLISECONDS), "the fake event reached the room");
	}

	@Test
	void aClientCannotPublishOnThePrivateQueueOfAnother() throws Exception {
		StompSession attacker = connect();

		attacker.send("/user/someone/queue/state", Map.of("status", "FINISHED"));

		assertClosed(attacker);
	}

	// ------------------------------------------------------------------ helpers

	private record Game(String code, String hostToken) {
	}

	private Game createGame() throws Exception {
		StompSession host = connect();
		BlockingQueue<Map<?, ?>> created = subscribe(host, "/user/queue/game");
		subscribe(host, "/user/queue/errors");
		settle();
		host.send("/app/game/create", Map.of("themeId", "general"));
		Map<?, ?> game = created.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		assertNotNull(game, "the game is created");
		return new Game((String) game.get("code"), (String) game.get("hostToken"));
	}

	private StompSession connect() throws Exception {
		StompSession session = stompClient.connectAsync("ws://localhost:" + port + "/ws",
				new StompSessionHandlerAdapter() {
				}).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
		sessions.add(session);
		return session;
	}

	private BlockingQueue<Map<?, ?>> subscribe(StompSession session, String destination) {
		BlockingQueue<Map<?, ?>> received = new LinkedBlockingQueue<>();
		session.subscribe(destination, new StompFrameHandler() {

			@Override
			public Type getPayloadType(StompHeaders headers) {
				return Map.class;
			}

			@Override
			public void handleFrame(StompHeaders headers, Object payload) {
				received.add((Map<?, ?>) payload);
			}

		});
		return received;
	}

	private void settle() throws InterruptedException {
		Thread.sleep(SETTLE_MS);
	}

	private void assertClosed(StompSession session) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
		while (session.isConnected() && System.nanoTime() < deadline) {
			Thread.sleep(20);
		}
		assertFalse(session.isConnected(), "the server must close the connection");
	}

}
