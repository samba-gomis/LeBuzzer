package com.lebuzzer.backend.service;

import com.lebuzzer.backend.dto.GameCreated;
import com.lebuzzer.backend.dto.GameSnapshot;
import com.lebuzzer.backend.dto.RoomEvent;

/**
 * Everything the game sends to clients goes through here. An interface so that the service
 * can be unit-tested without a WebSocket server (tests record the messages instead).
 *
 * {@code connectionId} is the name of the anonymous Principal of a connection.
 */
public interface GameNotifier {

	/** To every member of the game's room. */
	void broadcast(String gameCode, RoomEvent event);

	void sendGameCreated(String connectionId, GameCreated message);

	void sendSnapshot(String connectionId, GameSnapshot snapshot);

}
