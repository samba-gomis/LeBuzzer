package com.lebuzzer.backend.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;

import com.lebuzzer.backend.service.MessageMetrics;

/**
 * Sees every message on one of the two client channels (registered once per direction in WebSocketConfig)
 * and hands it to {@link MessageMetrics}. It only counts: the message goes on unchanged.
 */
public class MessageCounterInterceptor implements ChannelInterceptor {

	private final MessageMetrics metrics;
	private final MessageMetrics.Direction direction;

	public MessageCounterInterceptor(MessageMetrics metrics, MessageMetrics.Direction direction) {
		this.metrics = metrics;
		this.direction = direction;
	}

	@Override
	public Message<?> preSend(Message<?> message, MessageChannel channel) {
		metrics.record(direction, message);
		return message;
	}

}
