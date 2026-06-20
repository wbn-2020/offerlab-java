package com.offerlab.community.infra.web.handler;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.exception.SystemException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

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
        HttpStatus status = bizStatus(e.getCode());
        return ResponseEntity.status(status).body(r);
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
        return HttpStatus.OK;
    }

    @ExceptionHandler(SystemException.class)
    public ResponseEntity<Result<?>> handleSystem(SystemException e) {
        log.error("[sys] code={}", e.getCode(), e);
        Result<?> r = Result.fail(e.getCode(), e.getMessage());
        r.setTraceId(TraceContext.get());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<?>> handleDataAccess(DataAccessException e) {
        log.error("[db]", e);
        boolean missingSchema = looksLikeMissingSchema(e);
        String traceId = TraceContext.ensure();
        String message = missingSchema
                ? "数据库迁移未补齐，请先执行对应的 db/migration 增量脚本"
                : "数据库暂时不可用，请稍后重试";
        Result<?> r = Result.builder()
                .code(ErrorCode.DATABASE_ERROR.getCode())
                .message(message)
                .data(databaseDiagnostic(e, missingSchema, traceId))
                .traceId(traceId)
                .build();
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

    private boolean looksLikeMissingSchema(Throwable e) {
        if (e instanceof BadSqlGrammarException) {
            return true;
        }
        String message = String.valueOf(e.getMessage()).toLowerCase();
        return message.contains("unknown column")
                || message.contains("doesn't exist")
                || message.contains("does not exist")
                || message.contains("bad sql grammar");
    }

    private Map<String, Object> databaseDiagnostic(DataAccessException e, boolean missingSchema, String traceId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("errorCategory", missingSchema ? "SCHEMA_MISMATCH" : "DEPENDENCY_DOWN");
        data.put("schemaIssue", missingSchema);
        data.put("traceId", traceId);
        data.put("exceptionType", e.getClass().getSimpleName());
        data.put("affectedCapability", inferCapability(e));
        data.put("migrationHint", missingSchema
                ? "Run scripts/check-schema-readiness.mjs and apply the missing db/migration scripts after explicit confirmation."
                : "No schema migration hint is available for this error.");
        data.put("recommendedAction", missingSchema
                ? "Check /api/v1/ops/migration/status, then apply the listed migrations before retrying the user action."
                : "Check datasource connectivity, credentials, and database server health before retrying the user action.");
        return data;
    }

    private String inferCapability(Throwable e) {
        String message = String.valueOf(e.getMessage()).toLowerCase();
        if (message.contains("t_tag") || message.contains("tag_status") || message.contains("synonyms")) {
            return "TAG_GOVERNANCE";
        }
        if (message.contains("review_queue")) {
            return "REVIEW_QUEUE";
        }
        if (message.contains("mock_interview") || message.contains("ai_review")) {
            return "MOCK_INTERVIEW_AI_REVIEW";
        }
        if (message.contains("community_topic")) {
            return "COMMUNITY_TOPIC";
        }
        if (message.contains("search_index_retry")) {
            return "SEARCH_INDEX_RETRY";
        }
        if (message.contains("notif_retry")) {
            return "NOTIFICATION_RETRY";
        }
        return "DATABASE";
    }
}
