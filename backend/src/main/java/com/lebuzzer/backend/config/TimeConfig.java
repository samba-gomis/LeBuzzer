package com.lebuzzer.backend.config;

import java.time.Clock;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Everything about time: the server clock (the only one that counts) and the question timer. */
@Configuration
public class TimeConfig {

	/** Injected rather than calling System.currentTimeMillis(), so tests can move time forward. */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

	/**
	 * Runs the automatic close of each question when its time is up. One thread is enough:
	 * a close only takes a lock, updates scores and publishes one message.
	 */
	@Bean(destroyMethod = "shutdownNow")
	public ScheduledExecutorService questionTimer() {
		ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1,
				Thread.ofPlatform().name("question-timer-", 0).factory());
		// A cancelled timer (manual close) is removed right away instead of waiting for its delay.
		executor.setRemoveOnCancelPolicy(true);
		return executor;
	}

}
