package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerApiTest {

    @Test
    void permanentSqlErrorsReturn500WithoutInternalDiagnostics() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BrokenSchemaController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        // Audit item 5.6: schema state, exception class names, ops scripts and
        // internal endpoints must never reach the client. Ops correlates via
        // traceId against server logs instead.
        mvc.perform(get("/broken-schema"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.DATABASE_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("当前请求暂时无法完成，请稍后重试。"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void transientDatabaseErrorsReturn503WithoutInternalDiagnostics() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TransientDatabaseController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/transient-database"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.DATABASE_ERROR.getCode()))
                .andExpect(jsonPath("$.message")
                        .value("服务暂时无法完成请求，请稍后重试。"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void integrityErrorsReturn500WithoutConstraintDiagnostics() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new IntegrityFailureController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/integrity-failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.DATABASE_ERROR.getCode()))
                .andExpect(jsonPath("$.message").value("当前请求暂时无法完成，请稍后重试。"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void postNotFoundBusinessErrorReturnsHttp404() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new NotFoundController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/missing-post"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.POST_NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.POST_NOT_FOUND.getMessage()));
    }

    @Test
    void concurrentModificationBusinessErrorReturnsHttp409() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ConcurrentModificationController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/concurrent-modification"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(ErrorCode.CONCURRENT_MODIFICATION.getCode()))
                .andExpect(jsonPath("$.message").value(ErrorCode.CONCURRENT_MODIFICATION.getMessage()));
    }

    @RestController
    private static class BrokenSchemaController {
        @GetMapping("/broken-schema")
        Object brokenSchema() {
            throw new BadSqlGrammarException("select", "SELECT tag_status FROM t_tag",
                    new SQLException("Unknown column 'tag_status' in 'field list'"));
        }
    }

    @RestController
    private static class TransientDatabaseController {
        @GetMapping("/transient-database")
        Object transientDatabase() {
            throw new TransientDataAccessResourceException("connection pool exhausted");
        }
    }

    @RestController
    private static class IntegrityFailureController {
        @GetMapping("/integrity-failure")
        Object integrityFailure() {
            throw new DataIntegrityViolationException(
                    "duplicate value violates unique constraint user_email_key");
        }
    }

    @RestController
    private static class NotFoundController {
        @GetMapping("/missing-post")
        Object missingPost() {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
    }

    @RestController
    private static class ConcurrentModificationController {
        @GetMapping("/concurrent-modification")
        Object concurrentModification() {
            throw new BizException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }
}
