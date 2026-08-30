package com.seeker.share.common;

import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
		String detail = exception.getBindingResult().getAllErrors().stream()
				.map(MessageSourceResolvable::getDefaultMessage)
				.distinct()
				.filter(Objects::nonNull)
				.collect(Collectors.joining("；"));
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ProblemDetail handleHandlerValidation(HandlerMethodValidationException exception) {
		String detail = exception.getAllErrors().stream()
				.map(MessageSourceResolvable::getDefaultMessage)
				.distinct()
				.collect(Collectors.joining("；"));
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ProblemDetail handleNotReadable(HttpMessageNotReadableException exception) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求体格式错误，请检查请求内容");
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ProblemDetail handleStatus(ResponseStatusException exception) {
		String detail = exception.getReason() != null ? exception.getReason()
				: "请求失败 (" + exception.getStatusCode().value() + ")";
		return ProblemDetail.forStatusAndDetail(exception.getStatusCode(), detail);
	}
}
