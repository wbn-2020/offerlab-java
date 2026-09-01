package com.offerlab.community.infra.web.handler;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Result<?>> handleBiz(BizException e) {
        log.warn("[biz] code={} msg={}", e.getCode(), e.getMessage());
        boolean systemCode = isSystemCode(e.getCode());
        String message = systemCode ? safeSystemMessage(e.getCode()) : e.getMessage();
        Result<?> r = systemCode || e.getData() == null
                ? Result.fail(e.getCode(), message)
                : Result.builder().code(e.getCode()).message(message).data(e.getData()).build();
        r.setTraceId(TraceContext.get());
        HttpStatus status = bizStatus(e.getCode());
        return ResponseEntity.status(status).body(r);
    }

    private boolean isSystemCode(Integer code) {
        return code != null && code >= 20000 && code < 30000;
    }

    private String safeSystemMessage(Integer code) {
        if (ErrorCode.SYSTEM_ERROR.getCode().equals(code)) {
            return "系统暂时无法完成请求，请稍后重试。";
        }
        return "服务暂时不可用，请稍后重试。";
    }

    private HttpStatus bizStatus(Integer code) {
        if (ErrorCode.UNAUTHORIZED.getCode().equals(code)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if (ErrorCode.FORBIDDEN.getCode().equals(code)) {
            return HttpStatus.FORBIDDEN;
        }
        if (ErrorCode.RATE_LIMIT_EXCEEDED.getCode().equals(code)) {
            return HttpStatus.TOO_MANY_REQUESTS;
        }
        if (ErrorCode.RESOURCE_NOT_FOUND.getCode().equals(code)
                || ErrorCode.USER_NOT_FOUND.getCode().equals(code)
                || ErrorCode.POST_NOT_FOUND.getCode().equals(code)
                || ErrorCode.POST_DELETED.getCode().equals(code)
                || ErrorCode.COMMENT_NOT_FOUND.getCode().equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if (ErrorCode.PARAM_ERROR.getCode().equals(code)
                || ErrorCode.INVALID_REQUEST.getCode().equals(code)
                || ErrorCode.INVALID_STATUS.getCode().equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (ErrorCode.DUPLICATE_OPERATION.getCode().equals(code)
                || ErrorCode.CONCURRENT_MODIFICATION.getCode().equals(code)) {
            return HttpStatus.CONFLICT;
        }
        if (ErrorCode.DATABASE_ERROR.getCode().equals(code)
                || ErrorCode.CACHE_ERROR.getCode().equals(code)
                || ErrorCode.MQ_ERROR.getCode().equals(code)
                || ErrorCode.ELASTICSEARCH_ERROR.getCode().equals(code)
                || ErrorCode.DEPENDENCY_ERROR.getCode().equals(code)) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (code != null && code >= 30000 && code < 40000) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code != null && code >= 20000 && code < 30000) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return HttpStatus.BAD_REQUEST;
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<Result<?>> handleSystem(SystemException e) {
        log.error("[sys] code={}", e.getCode(), e);
        Result<?> r = Result.fail(e.getCode(), safeSystemMessage(e.getCode()));
        r.setTraceId(TraceContext.get());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(r);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<?>> handleDataAccess(DataAccessException e) {
        String traceId = TraceContext.ensure();
        boolean retryable = retryableDatabaseFailure(e);
        HttpStatus status = retryable
                ? HttpStatus.SERVICE_UNAVAILABLE
                : HttpStatus.INTERNAL_SERVER_ERROR;
        // Diagnostics stay server-side and are correlated through traceId.
        log.error("[db] category={} retryable={} capability={} traceId={}",
                databaseFailureCategory(e, retryable), retryable, affectedCapability(e), traceId, e);
        Result<?> r = Result.builder()
                .code(ErrorCode.DATABASE_ERROR.getCode())
                .message(retryable
                        ? "服务暂时无法完成请求，请稍后重试。"
                        : "当前请求暂时无法完成，请稍后重试。")
                .traceId(traceId)
                .build();
        return ResponseEntity.status(status).body(r);
    }

    private boolean retryableDatabaseFailure(Throwable e) {
        if (e instanceof DataIntegrityViolationException
                || e instanceof InvalidDataAccessResourceUsageException) {
            return false;
        }
        Throwable current = e;
        while (current != null) {
            if (current instanceof TransientDataAccessException
                    || current instanceof RecoverableDataAccessException
                    || current instanceof DataAccessResourceFailureException
                    || current instanceof SQLTransientException
                    || current instanceof SQLRecoverableException) {
                return true;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return false;
    }

    private String databaseFailureCategory(Throwable e, boolean retryable) {
        if (schemaIssue(e)) {
            return "SQL_OR_SCHEMA_ERROR";
        }
        if (e instanceof DataIntegrityViolationException) {
            return "DATA_INTEGRITY_ERROR";
        }
        return retryable ? "TRANSIENT_DATABASE_ERROR" : "PERMANENT_DATABASE_ERROR";
    }

    private boolean schemaIssue(Throwable e) {
        String message = e == null ? "" : String.valueOf(e.getMessage()).toLowerCase();
        return message.contains("unknown column")
                || message.contains("unknown table")
                || message.contains("doesn't exist")
                || message.contains("bad sql grammar");
    }

    private String affectedCapability(Throwable e) {
        String message = e == null ? "" : String.valueOf(e.getMessage()).toLowerCase();
        if (message.contains("tag_") || message.contains("t_tag")) {
            return "TAG_GOVERNANCE";
        }
        return "UNKNOWN";
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<?>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors().forEach(error ->
                fieldErrors.putIfAbsent("request", error.getDefaultMessage()));
        log.warn("[validation] fields={}", fieldErrors.keySet());
        Result<?> r = Result.builder()
                .code(ErrorCode.PARAM_ERROR.getCode())
                .message("请检查填写内容")
                .data(Map.of("fieldErrors", fieldErrors))
                .build();
        r.setTraceId(TraceContext.get());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);
    }

    @ExceptionHandler({
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
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(r);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<?>> handleNoResource(NoResourceFoundException e) {
        log.warn("[not-found] {}", e.getMessage());
        Result<?> r = Result.fail(ErrorCode.RESOURCE_NOT_FOUND);
        r.setTraceId(TraceContext.get());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(r);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[method-not-allowed] {}", e.getMessage());
        Result<?> r = Result.fail(ErrorCode.INVALID_REQUEST);
        r.setTraceId(TraceContext.get());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(r);
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<?>> handleAll(Throwable e) {
        log.error("[unhandled]", e);
        Result<?> r = Result.fail(ErrorCode.SYSTEM_ERROR);
        r.setTraceId(TraceContext.get());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
    }

}
