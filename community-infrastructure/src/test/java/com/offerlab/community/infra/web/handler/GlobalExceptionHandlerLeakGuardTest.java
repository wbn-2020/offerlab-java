package com.offerlab.community.infra.web.handler;

import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerLeakGuardTest {

    @AfterEach
    void clearTrace() {
        TraceContext.clear();
    }

    @Test
    void databaseFailuresMustNotLeakInternalDiagnosticsToClients() throws Exception {
        String handler = Files.readString(
                Path.of("src/main/java/com/offerlab/community/infra/web/handler/GlobalExceptionHandler.java"),
                StandardCharsets.UTF_8);

        // Internal runbook hints, schema state, and ops endpoints stay out of
        // client payloads (audit item 5.6).
        assertFalse(handler.contains("migrationHint"),
                "client payloads must not carry migration runbook hints");
        assertFalse(handler.contains("recommendedAction"),
                "client payloads must not carry internal remediation steps");
        assertFalse(handler.contains("check-schema-readiness"),
                "client payloads must not name internal ops scripts");
        assertFalse(handler.contains("/api/v1/ops/migration/status"),
                "client payloads must not advertise internal ops endpoints");
        assertFalse(handler.contains("databaseDiagnostic"),
                "the diagnostic payload builder must stay deleted");
        assertFalse(handler.contains("exceptionType"),
                "client payloads must not expose backend exception class names");

        int dataAccessStart = handler.indexOf("public ResponseEntity<Result<?>> handleDataAccess(");
        assertTrue(dataAccessStart >= 0, "DataAccessException handler must exist");
        String dataAccessBody = handler.substring(
                dataAccessStart, handler.indexOf("private boolean retryableDatabaseFailure(", dataAccessStart));
        assertFalse(dataAccessBody.contains(".data("),
                "database error responses must not attach a diagnostic data payload");
        assertTrue(dataAccessBody.contains("HttpStatus.SERVICE_UNAVAILABLE")
                        && dataAccessBody.contains("HttpStatus.INTERNAL_SERVER_ERROR"),
                "database errors must distinguish retryable 503 from permanent 500 failures");
        assertTrue(dataAccessBody.contains("traceId"),
                "clients must still receive a traceId to correlate support requests");
        assertTrue(dataAccessBody.contains("log.error"),
                "full diagnostics must be preserved in server-side logs");
    }

    @Test
    void retryableAndRecoverableDatabaseFailuresReturn503() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertGenericDatabaseResponse(
                handler.handleDataAccess(new TransientDataAccessResourceException("pool exhausted")),
                HttpStatus.SERVICE_UNAVAILABLE);
        assertGenericDatabaseResponse(
                handler.handleDataAccess(new RecoverableDataAccessException("connection can be recovered")),
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void permanentSqlAndIntegrityFailuresReturn500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertGenericDatabaseResponse(
                handler.handleDataAccess(new InvalidDataAccessResourceUsageException(
                        "unknown column secret_schema.internal_flag")),
                HttpStatus.INTERNAL_SERVER_ERROR);
        assertGenericDatabaseResponse(
                handler.handleDataAccess(new DataIntegrityViolationException(
                        "duplicate key violates user_email_key")),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private static void assertGenericDatabaseResponse(ResponseEntity<Result<?>> response,
                                                      HttpStatus expectedStatus) {
        assertEquals(expectedStatus, response.getStatusCode());
        Result<?> body = response.getBody();
        assertNotNull(body);
        assertEquals(ErrorCode.DATABASE_ERROR.getCode(), body.getCode());
        assertNotNull(body.getTraceId());
        assertFalse(body.getTraceId().isBlank());
        assertNull(body.getData());
        assertFalse(body.getMessage().contains("column"));
        assertFalse(body.getMessage().contains("constraint"));
        assertFalse(body.getMessage().contains("schema"));
        assertFalse(body.getMessage().contains("Database"));
        assertFalse(body.getMessage().contains("database"));
        assertFalse(body.getMessage().contains("数据库"));
    }
}
