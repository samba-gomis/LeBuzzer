package com.lebuzzer.backend.dto;

/**
 * GET /api/metrics/{code}: every STOMP message that really went over the WebSockets of one game.
 *
 * @param connections WebSocket connections that took part in the game (host and players, reconnections included)
 * @param applicationMessages SEND frames from clients and MESSAGE frames to clients (the useful content)
 * @param heartbeats keep-alive frames, in both directions
 * @param protocolFrames CONNECT, CONNECTED, SUBSCRIBE, DISCONNECT...
 */
public record GameMessageMetrics(String code, int connections, long total, long applicationMessages, long heartbeats,
		long protocolFrames, DirectionCounts clientToServer, DirectionCounts serverToClient) {
}
