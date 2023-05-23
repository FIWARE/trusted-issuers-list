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

import java.net.URI;

@Produces
@Singleton
@Requires(classes = { ConflictException.class, ExceptionHandler.class })
@Slf4j
public class ConflictExceptionHandler
		implements ExceptionHandler<ConflictException, HttpResponse<ProblemDetailsVO>> {

	@Override public HttpResponse<ProblemDetailsVO> handle(HttpRequest request, ConflictException exception) {
		return HttpResponse.status(HttpStatus.CONFLICT).body(new ProblemDetailsVO()
				.status(HttpStatus.CONFLICT.getCode())
				.detail(exception.getLocalizedMessage())
				.instance(URI.create(exception.getEntityId()))
				.title("Conflict."));
	}
}
