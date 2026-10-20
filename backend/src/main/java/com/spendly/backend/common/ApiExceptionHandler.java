package com.spendly.backend.common;

import com.spendly.backend.auth.LoginRateLimiter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import jakarta.validation.ConstraintViolationException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(LoginRateLimiter.LimitExceeded.class)
    ResponseEntity<ApiError> rateLimited(LoginRateLimiter.LimitExceeded exception) {
        return ResponseEntity.status(429).header("Retry-After", Long.toString(exception.getRetryAfter()))
                .body(ApiError.of("LOGIN_RATE_LIMITED", "잠시 후 다시 로그인해 주세요."));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> apiError(ApiException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(ApiError.of(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> authenticationFailed() {
        return ResponseEntity.status(401).body(ApiError.of("INVALID_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidInput(MethodArgumentNotValidException exception) {
        List<ApiError.Detail> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.Detail(error.getField(), "입력 형식 또는 길이가 올바르지 않습니다."))
                .distinct().toList();
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "입력값을 확인해 주세요.", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> invalidServiceInput() {
        return ResponseEntity.badRequest().body(ApiError.of("VALIDATION_ERROR", "입력값을 확인해 주세요."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> invalidJson() {
        return ResponseEntity.badRequest().body(ApiError.of("INVALID_JSON", "JSON 요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidParameter(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR", "요청 형식을 확인해 주세요.",
                List.of(new ApiError.Detail(exception.getName(), "요청 형식이 올바르지 않습니다."))));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMethod() {
        return ResponseEntity.status(405).body(ApiError.of("METHOD_NOT_ALLOWED", "지원하지 않는 요청 방식입니다."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(404).body(ApiError.of("NOT_FOUND", "요청한 리소스를 찾을 수 없습니다."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMediaType() {
        return ResponseEntity.status(415).body(ApiError.of("UNSUPPORTED_MEDIA_TYPE", "JSON 형식으로 요청해 주세요."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpectedError() {
        // Do not include SQL, request values or authentication details in logs/responses.
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "요청을 처리하지 못했습니다."));
    }
}
