package com.lebuzzer.backend.service;

import java.util.Objects;

import com.lebuzzer.backend.model.ParticipantRole;

/**
 * Who is behind a WebSocket connection: the host of a game, or one of its players.
 *
 * @param playerId secret player id, {@code null} for the host
 */
record ConnectionInfo(String gameCode, ParticipantRole role, String playerId) {

	static ConnectionInfo host(String gameCode) {
		return new ConnectionInfo(gameCode, ParticipantRole.HOST, null);
	}

	static ConnectionInfo player(String gameCode, String playerId) {
		return new ConnectionInfo(gameCode, ParticipantRole.PLAYER, playerId);
	}

	boolean isSameParticipant(String otherGameCode, ParticipantRole otherRole, String otherPlayerId) {
		return gameCode.equals(otherGameCode) && role == otherRole && Objects.equals(playerId, otherPlayerId);
	}

}
