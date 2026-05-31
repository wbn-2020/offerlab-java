package com.offerlab.community.archtest;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.AdminRoleMapper;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.web.handler.GlobalExceptionHandler;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.infra.web.config.WebMvcConfig;
import com.offerlab.community.feed.controller.FeedController;
import com.offerlab.community.interaction.controller.InteractionController;
import com.offerlab.community.notification.controller.NotificationController;
import com.offerlab.community.post.controller.PostController;
import com.offerlab.community.question.controller.QuestionController;
import com.offerlab.community.user.controller.AuthController;
import com.offerlab.community.user.controller.UserController;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionSecurityGuardTest {

    @Test
    void publicAuthMutationEndpointsAreRateLimited() throws Exception {
        assertRateLimited(AuthController.class, "register", AuthController.RegisterReq.class, jakarta.servlet.http.HttpServletRequest.class);
        assertRateLimited(AuthController.class, "login", AuthController.LoginReq.class, jakarta.servlet.http.HttpServletRequest.class);
    }

    @Test
    void highFrequencyUserMutationEndpointsAreRateLimited() throws Exception {
        assertRateLimited(InteractionController.class, "like", Long.class);
        assertRateLimited(InteractionController.class, "unlike", Long.class);
        assertRateLimited(InteractionController.class, "favorite", Long.class);
        assertRateLimited(InteractionController.class, "unfavorite", Long.class);
        assertRateLimited(InteractionController.class, "comment", Long.class, InteractionController.CommentReq.class);
        assertRateLimited(InteractionController.class, "deleteComment", Long.class);
        assertRateLimited(InteractionController.class, "likeComment", Long.class);
        assertRateLimited(InteractionController.class, "unlikeComment", Long.class);
        assertRateLimited(PostController.class, "publish", PostController.PublishReq.class);
        assertRateLimited(PostController.class, "update", Long.class, PostController.UpdateReq.class);
        assertRateLimited(PostController.class, "delete", Long.class);
        assertRateLimited(FeedController.class, "feedback", com.offerlab.community.feed.api.dto.FeedFeedbackCmd.class);
        assertRateLimited(QuestionController.class, "favorite", Long.class);
        assertRateLimited(QuestionController.class, "unfavorite", Long.class);
        assertRateLimited(QuestionController.class, "progress", Long.class, QuestionController.ProgressReq.class);
        assertRateLimited(QuestionController.class, "addPrepTarget", com.offerlab.community.question.api.dto.PrepTargetCmd.class);
        assertRateLimited(QuestionController.class, "deletePrepTarget", Long.class);
        assertRateLimited(NotificationController.class, "read", NotificationController.ReadReq.class);
        assertRateLimited(NotificationController.class, "readAll");
        assertRateLimited(UserController.class, "updateMe", UserController.UpdateProfileReq.class);
        assertRateLimited(UserController.class, "changePassword", UserController.ChangePasswordReq.class);
        assertRateLimited(UserController.class, "logoutAll");
        assertRateLimited(UserController.class, "updateIntent", com.offerlab.community.user.api.dto.UserIntentDTO.class);
        assertRateLimited(UserController.class, "updatePrivacySettings", com.offerlab.community.user.api.dto.UserPrivacySettingDTO.class);
        assertRateLimited(UserController.class, "follow", Long.class);
        assertRateLimited(UserController.class, "unfollow", Long.class);
    }

    @Test
    void prodProfileRejectsLocalCorsOrigins() throws Exception {
        WebMvcConfig config = new WebMvcConfig(null, prodEnvironment());
        setField(config, "allowedOrigins", "http://localhost:5173,http://127.0.0.1:5174");

        assertThrows(IllegalStateException.class, () -> invokeValidateCorsOrigins(config));
    }

    @Test
    void prodProfileAllowsExplicitCorsOrigins() throws Exception {
        WebMvcConfig config = new WebMvcConfig(null, prodEnvironment());
        setField(config, "allowedOrigins", "https://app.offerlab.example");

        invokeValidateCorsOrigins(config);
    }

    @Test
    void prodProfileRejectsDefaultJwtSecret() throws Exception {
        JwtService jwtService = new JwtService(nullRedis());
        setField(jwtService, "environment", prodEnvironment());
        setField(jwtService, "secret", "offerlab-dev-secret-key-please-change-in-prod-1234567890abcdef");

        assertThrows(IllegalStateException.class, () -> invokeValidateSecret(jwtService));
    }

    @Test
    void prodProfileDoesNotAllowLocalOpenAdminMode() {
        AdminPermissionService service = new AdminPermissionService("", true, mapperWithoutAdminTable(), prodEnvironment());

        assertFalse(service.isLocalOpenMode());
        assertEquals("LOCKED", service.mode());
        assertThrows(BizException.class, () -> service.requireAdmin(10001L));
    }

    @Test
    void devProfileLocksAdminModeUnlessLocalOpenIsExplicitlyEnabled() {
        AdminPermissionService service = new AdminPermissionService("", false, mapperWithoutAdminTable(), devEnvironment());

        assertFalse(service.isLocalOpenMode());
        assertEquals("LOCKED", service.mode());
        assertThrows(BizException.class, () -> service.requireAdmin(10001L));
    }

    @Test
    void devProfileAllowsExplicitLocalOpenAdminModeForBootstrap() {
        AdminPermissionService service = new AdminPermissionService("", true, mapperWithoutAdminTable(), devEnvironment());

        assertEquals("LOCAL_OPEN", service.mode());
        service.requireAdmin(10001L);
    }

    @Test
    void prodConfigMustKeepPublicDocsAndLocalBootstrapClosed() throws Exception {
        String prodConfig = Files.readString(Path.of("../community-bootstrap/src/main/resources/application-prod.yml"), StandardCharsets.UTF_8);
        String baseConfig = Files.readString(Path.of("../community-bootstrap/src/main/resources/application.yml"), StandardCharsets.UTF_8);
        String devConfig = Files.readString(Path.of("../community-bootstrap/src/main/resources/application-dev.yml"), StandardCharsets.UTF_8);

        assertTrue(baseConfig.contains("api-docs:\n    path: /v3/api-docs\n    enabled: false") || baseConfig.contains("api-docs:\r\n    path: /v3/api-docs\r\n    enabled: false"), "base config must disable OpenAPI docs by default");
        assertTrue(baseConfig.contains("swagger-ui:\n    path: /swagger-ui.html\n    enabled: false") || baseConfig.contains("swagger-ui:\r\n    path: /swagger-ui.html\r\n    enabled: false"), "base config must disable Swagger UI by default");
        assertTrue(devConfig.contains("api-docs:\n    enabled: true") || devConfig.contains("api-docs:\r\n    enabled: true"), "dev profile must explicitly enable OpenAPI docs");
        assertTrue(devConfig.contains("swagger-ui:\n    enabled: true") || devConfig.contains("swagger-ui:\r\n    enabled: true"), "dev profile must explicitly enable Swagger UI");
        assertTrue(prodConfig.contains("api-docs:\n    enabled: false") || prodConfig.contains("api-docs:\r\n    enabled: false"), "prod must disable OpenAPI docs");
        assertTrue(prodConfig.contains("swagger-ui:\n    enabled: false") || prodConfig.contains("swagger-ui:\r\n    enabled: false"), "prod must disable Swagger UI");
        assertFalse(prodConfig.contains("local-open-enabled: true"), "prod must not enable local-open admin bootstrap");
        assertTrue(prodConfig.contains("secret: ${JWT_SECRET}"), "prod must require an external JWT secret");
        assertTrue(prodConfig.contains("allowed-origins: ${OFFERLAB_WEB_CORS_ALLOWED_ORIGINS}"), "prod must require explicit CORS origins");
    }

    @Test
    void parameterErrorsMustReturnHttp400() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<?> response = handler.handleParam(new IllegalArgumentException("bad request"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
    private static Environment prodEnvironment() {
        return profiles("prod");
    }

    private static Environment devEnvironment() {
        return profiles("dev");
    }

    private static Environment profiles(String... activeProfiles) {
        return (Environment) java.lang.reflect.Proxy.newProxyInstance(
                Environment.class.getClassLoader(),
                new Class[]{Environment.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "matchesProfiles" -> {
                        String[] expressions = (String[]) args[0];
                        boolean matched = java.util.Arrays.stream(expressions)
                                .anyMatch(expression -> java.util.Arrays.asList(activeProfiles).contains(expression));
                        yield matched;
                    }
                    case "getActiveProfiles" -> activeProfiles;
                    case "getDefaultProfiles" -> new String[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static AdminRoleMapper mapperWithoutAdminTable() {
        return new AdminRoleMapper() {
            @Override
            public int tableExists() {
                return 0;
            }

            @Override
            public int countActiveAdmin(Long uid) {
                return 0;
            }

            @Override
            public int countActiveRole(Long uid, String roleCode) {
                return 0;
            }

            @Override
            public int countEnabledAdmins() {
                return 0;
            }

            @Override
            public int countAdminRows() {
                return 0;
            }

            @Override
            public java.util.List<java.util.Map<String, Object>> listAdmins(int limit) {
                return java.util.List.of();
            }

            @Override
            public int upsertAdmin(Long uid, String roleCode, String remark, Long operatorUid) {
                return 0;
            }

            @Override
            public int updateAdminStatus(Long uid, String roleCode, int enabled, String remark, Long operatorUid) {
                return 0;
            }
        };
    }

    private static StringRedisTemplate nullRedis() {
        return null;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invokeValidateSecret(JwtService jwtService) throws Exception {
        Method method = JwtService.class.getDeclaredMethod("validateSecret");
        method.setAccessible(true);
        try {
            method.invoke(jwtService);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    private static void assertRateLimited(Class<?> controllerClass, String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = controllerClass.getDeclaredMethod(methodName, parameterTypes);
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        org.junit.jupiter.api.Assertions.assertNotNull(rateLimit, controllerClass.getSimpleName() + "." + methodName + " must be rate limited");
        org.junit.jupiter.api.Assertions.assertFalse(rateLimit.key().isBlank(), controllerClass.getSimpleName() + "." + methodName + " rate limit key must not be blank");
    }

    private static void invokeValidateCorsOrigins(WebMvcConfig config) throws Exception {
        Method method = WebMvcConfig.class.getDeclaredMethod("validateCorsOrigins");
        method.setAccessible(true);
        try {
            method.invoke(config);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }
}
