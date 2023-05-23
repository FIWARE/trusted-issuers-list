package org.fiware.iam.exception;

import lombok.Getter;

/**
 * Exception to be thrown in all conflict-cases
 */
public class ConflictException extends RuntimeException {

	@Getter
	private final String entityId;

	public ConflictException(String message, String entityId) {
		super(message);
		this.entityId = entityId;
	}

	public ConflictException(String message, Throwable cause, String entityId) {
		super(message, cause);
		this.entityId = entityId;
	}
}
