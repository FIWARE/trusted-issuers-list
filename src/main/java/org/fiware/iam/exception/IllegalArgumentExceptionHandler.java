package org.fiware.iam.exception;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.fiware.iam.tir.model.ProblemDetailsVO;

/**
 * Catch all {@link IllegalArgumentException} and translate them into proper 400 responses
 */
@Produces
@Singleton
@Requires(classes = { IllegalArgumentException.class, ExceptionHandler.class })
@Slf4j
public class IllegalArgumentExceptionHandler
		implements ExceptionHandler<IllegalArgumentException, HttpResponse<ProblemDetailsVO>> {

	@Override public HttpResponse<ProblemDetailsVO> handle(HttpRequest request, IllegalArgumentException exception) {
		log.info("Received an illegal argument for request {}.", request, exception);
		return HttpResponse.badRequest(new ProblemDetailsVO()
				.status(HttpStatus.BAD_REQUEST.getCode())
				.detail(exception.getLocalizedMessage())
				.title("Received an invalid issuer configuration."));
	}
}
