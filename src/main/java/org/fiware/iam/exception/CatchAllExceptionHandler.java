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

import java.time.format.DateTimeParseException;

/**
 * Handler to catch and log all unexpected exceptions and translate them into a proper 500 response.
 */
@Produces
@Singleton
@Requires(classes = { Exception.class, ExceptionHandler.class })
@Slf4j
public class CatchAllExceptionHandler implements ExceptionHandler<Exception, HttpResponse<ProblemDetailsVO>> {

	@Override
	public HttpResponse<ProblemDetailsVO> handle(HttpRequest request, Exception exception) {
		log.warn("Received unexpected exception {} for request {}.", exception.getMessage(), request, exception);

		return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(
						new ProblemDetailsVO()
								.status(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
								.detail("Request could not be answered due to an unexpected internal error.")
								.title(HttpStatus.INTERNAL_SERVER_ERROR.getReason()));
	}
}
