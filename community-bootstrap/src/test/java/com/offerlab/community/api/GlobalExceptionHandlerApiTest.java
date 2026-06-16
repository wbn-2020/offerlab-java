package com.offerlab.community.api;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.infra.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
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
    void missingSchemaSqlErrorsReturnStructuredDatabaseDiagnostic() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BrokenSchemaController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/broken-schema"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(ErrorCode.DATABASE_ERROR.getCode()))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.data.errorCategory").value("SCHEMA_MISMATCH"))
                .andExpect(jsonPath("$.data.schemaIssue").value(true))
                .andExpect(jsonPath("$.data.exceptionType").value("BadSqlGrammarException"))
                .andExpect(jsonPath("$.data.affectedCapability").value("TAG_GOVERNANCE"))
                .andExpect(jsonPath("$.data.migrationHint").value("Run scripts/check-schema-readiness.mjs and apply the missing db/migration scripts after explicit confirmation."))
                .andExpect(jsonPath("$.data.recommendedAction").value("Check /api/v1/ops/migration/status, then apply the listed migrations before retrying the user action."))
                .andExpect(jsonPath("$.data.traceId").isNotEmpty());
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

    @RestController
    private static class BrokenSchemaController {
        @GetMapping("/broken-schema")
        Object brokenSchema() {
            throw new BadSqlGrammarException("select", "SELECT tag_status FROM t_tag",
                    new SQLException("Unknown column 'tag_status' in 'field list'"));
        }
    }

    @RestController
    private static class NotFoundController {
        @GetMapping("/missing-post")
        Object missingPost() {
            throw new BizException(ErrorCode.POST_NOT_FOUND);
        }
    }
}
