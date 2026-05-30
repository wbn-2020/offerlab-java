package com.offerlab.community.infra.web.handler;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<?>> handleBiz(BizException e) {
        log.warn("[biz] code={} msg={}", e.getCode(), e.getMessage());
        Result<?> r = e.getData() == null
                ? Result.fail(e.getCode(), e.getMessage())
                : Result.builder().code(e.getCode()).message(e.getMessage()).data(e.getData()).build();
        r.setTraceId(TraceContext.get());
        HttpStatus status = e.getCode() == ErrorCode.UNAUTHORIZED.getCode()
                ? HttpStatus.UNAUTHORIZED
                : e.getCode() == ErrorCode.FORBIDDEN.getCode()
                    ? HttpStatus.FORBIDDEN
                    : e.getCode() == ErrorCode.RATE_LIMIT_EXCEEDED.getCode()
                        ? HttpStatus.TOO_MANY_REQUESTS
                        : HttpStatus.OK;
        return ResponseEntity.status(status).body(r);
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<Result<?>> handleSystem(SystemException e) {
        log.error("[sys] code={}", e.getCode(), e);
        Result<?> r = Result.fail(ErrorCode.SYSTEM_ERROR);
        r.setTraceId(TraceContext.get());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class,
            ConstraintViolationException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Result<?>> handleParam(Exception e) {
        log.warn("[param] {}", e.getMessage());
        Result<?> r = Result.fail(ErrorCode.PARAM_ERROR);
        r.setTraceId(TraceContext.get());
        return ResponseEntity.ok(r);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<?>> handleAll(Throwable e) {
        log.error("[unhandled]", e);
        Result<?> r = Result.fail(ErrorCode.SYSTEM_ERROR);
        r.setTraceId(TraceContext.get());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
    }
}
