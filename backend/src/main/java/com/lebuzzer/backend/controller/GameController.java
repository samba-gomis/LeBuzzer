package com.lebuzzer.backend.controller;

import java.security.Principal;

import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.lebuzzer.backend.dto.AnswerRequest;
import com.lebuzzer.backend.dto.CreateGameRequest;
import com.lebuzzer.backend.dto.ErrorCode;
import com.lebuzzer.backend.dto.ErrorMessage;
import com.lebuzzer.backend.dto.HostTokenRequest;
import com.lebuzzer.backend.dto.JoinRequest;
import com.lebuzzer.backend.service.GameException;
import com.lebuzzer.backend.service.GameService;

/**
 * Receives the STOMP messages sent to /app/game/... and hands them to {@link GameService}.
 * No rule here, exactly like a REST controller: the service decides and publishes.
 *
 * The connection is identified by the name of its anonymous Principal (one UUID per connection).
 */
@Controller
public class GameController {

	private final GameService gameService;

	public GameController(GameService gameService) {
		this.gameService = gameService;
	}

	@MessageMapping("/game/create")
	public void create(CreateGameRequest request, Principal principal) {
		gameService.createGame(principal.getName(), request.themeId());
	}

	@MessageMapping("/game/{code}/join")
	public void join(@DestinationVariable String code, JoinRequest request, Principal principal) {
		gameService.join(code, principal.getName(), request.nickname(), request.playerId());
	}

	@MessageMapping("/game/{code}/host-join")
	public void hostJoin(@DestinationVariable String code, HostTokenRequest request, Principal principal) {
		gameService.hostJoin(code, principal.getName(), request.hostToken());
	}

	@MessageMapping("/game/{code}/sync")
	public void sync(@DestinationVariable String code, Principal principal) {
		gameService.sync(code, principal.getName());
	}

	@MessageMapping("/game/{code}/answer")
	public void answer(@DestinationVariable String code, AnswerRequest request, Principal principal) {
		gameService.submitAnswer(code, principal.getName(), request.questionId(), request.choiceIndex());
	}

	@MessageMapping("/game/{code}/start-question")
	public void startQuestion(@DestinationVariable String code, HostTokenRequest request) {
		gameService.startQuestion(code, request.hostToken());
	}

	@MessageMapping("/game/{code}/close-question")
	public void closeQuestion(@DestinationVariable String code, HostTokenRequest request) {
		gameService.closeQuestion(code, request.hostToken());
	}

	@MessageMapping("/game/{code}/end")
	public void end(@DestinationVariable String code, HostTokenRequest request) {
		gameService.endGame(code, request.hostToken());
	}

	/** A refused action goes back privately to the connection that sent it; nothing is broadcast. */
	@MessageExceptionHandler
	@SendToUser(destinations = "/queue/errors", broadcast = false)
	public ErrorMessage handleGameError(GameException exception) {
		return new ErrorMessage(exception.getCode(), exception.getMessage());
	}

	/** Unreadable JSON or empty body. */
	@MessageExceptionHandler({ MessageConversionException.class, MethodArgumentNotValidException.class })
	@SendToUser(destinations = "/queue/errors", broadcast = false)
	public ErrorMessage handleBadMessage(Exception exception) {
		return new ErrorMessage(ErrorCode.INVALID_REQUEST, "The message could not be read");
	}

}
