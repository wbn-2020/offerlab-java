package com.offerlab.community.question.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionAdminRiskConfirmationGuardTest {

    @Test
    void adminQuestionAndCompanyAliasWritesMustRequireRiskRemarks() throws Exception {
        String controllerSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/controller/QuestionAdminController.java"), StandardCharsets.UTF_8);
        String updateCmdSource = Files.readString(Path.of("src/main/java/com/offerlab/community/question/api/dto/QuestionAdminUpdateCmd.java"), StandardCharsets.UTF_8);

        assertTrue(updateCmdSource.contains("private String confirmationPhrase"),
                "question admin update DTO must expose confirmation metadata for future stronger gates");
        assertTrue(controllerSource.contains("String remark = RiskConfirmation.requireHigh(cmd.getRemark())"),
                "question edits must require an explicit risk remark");
        assertTrue(controllerSource.contains("recordRequired(uid, \"QUESTION_UPDATE\", \"QUESTION\", id, null, dto,\n                remark)"),
                "question edits must audit the required risk remark");
        assertTrue(controllerSource.contains("String remark = RiskConfirmation.requireHigh(cmd == null ? null : cmd.getRemark())"),
                "company alias create/update must require explicit risk remarks");
        assertTrue(controllerSource.contains("String remark = RiskConfirmation.requireHigh(request == null ? null : request.remark())"),
                "company alias status changes must require explicit risk remarks");
        assertTrue(controllerSource.contains("recordRequired(uid, \"COMPANY_ALIAS_CREATE\", \"COMPANY_ALIAS\", dto.getId(), null, dto,\n                remark)"),
                "company alias create must audit the required risk remark");
        assertTrue(controllerSource.contains("recordRequired(uid, \"COMPANY_ALIAS_UPDATE\", \"COMPANY_ALIAS\", id, null, dto,\n                remark)"),
                "company alias update must audit the required risk remark");
        assertTrue(controllerSource.contains("recordRequired(uid, \"COMPANY_ALIAS_STATUS\", \"COMPANY_ALIAS\", id, null, result,\n                remark)"),
                "company alias status changes must audit the required risk remark");
    }
}
