package com.skillskeeper.skillskeeper.support;

import java.util.List;

import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * Captures what a class logged, so a test can assert that a failure hidden from the client was still
 * recorded for an operator.
 */
public final class LogCapture implements AutoCloseable {

	private final Logger logger;
	private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

	private LogCapture(Class<?> type) {
		this.logger = (Logger) LoggerFactory.getLogger(type);
		this.appender.start();
		this.logger.addAppender(this.appender);
	}

	public static LogCapture of(Class<?> type) {
		return new LogCapture(type);
	}

	public List<String> messagesAtOrAbove(Level level) {
		return this.appender.list.stream()
				.filter(event -> event.getLevel().isGreaterOrEqual(level))
				.map(ILoggingEvent::getFormattedMessage)
				.toList();
	}

	public List<String> warningsAndWorse() {
		return messagesAtOrAbove(Level.WARN);
	}

	@Override
	public void close() {
		this.logger.detachAppender(this.appender);
		this.appender.stop();
	}
}
