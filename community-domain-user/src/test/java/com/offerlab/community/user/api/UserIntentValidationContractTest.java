package com.offerlab.community.user.api;

import com.offerlab.community.user.api.dto.UserIntentDTO;
import com.offerlab.community.user.controller.UserController;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserIntentValidationContractTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void userIntentDtoRejectsOversizedAndOutOfRangeFields() {
        UserIntentDTO dto = UserIntentDTO.builder()
                .targetCompanies(List.of(
                        "OpenAI", "Anthropic", "DeepMind", "Microsoft", "Google", "ByteDance", "Alibaba", "Tencent",
                        "Baidu", "Meituan", "JD", "Didi", "Kuaishou", "Xiaohongshu", "Shopee", "Grab", "AWS",
                        "Azure", "Cloudflare", "Databricks", "Snowflake"))
                .targetPositions(List.of("x".repeat(65)))
                .yearsOfExp(51)
                .expectedCity("x".repeat(65))
                .expectedSalaryRange(UserIntentDTO.SalaryRange.builder()
                        .min(-1)
                        .max(1001)
                        .unit("x".repeat(17))
                        .build())
                .build();

        var violations = validator.validate(dto);

        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("targetCompanies")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("targetPositions")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("yearsOfExp")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("expectedCity")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("expectedSalaryRange.min")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("expectedSalaryRange.max")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().startsWith("expectedSalaryRange.unit")));
    }

    @Test
    void userControllerUpdateIntentRequiresValidRequestBody() throws Exception {
        Method method = UserController.class.getMethod("updateIntent", UserIntentDTO.class);
        Annotation[] parameterAnnotations = method.getParameterAnnotations()[0];

        assertTrue(
                java.util.Arrays.stream(parameterAnnotations).anyMatch(annotation -> annotation.annotationType() == Valid.class),
                "updateIntent should validate UserIntentDTO before reaching the service");
    }
}
