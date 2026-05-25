package com.offerlab.community.archtest;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.infra.security.AdminPermissionService;
import com.offerlab.community.infra.security.AdminRoleMapper;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.infra.web.config.WebMvcConfig;
import com.offerlab.community.user.controller.AuthController;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecurityGuardTest {

    @Test
    void publicAuthMutationEndpointsAreRateLimited() throws Exception {
        assertRateLimited("register", AuthController.RegisterReq.class, jakarta.servlet.http.HttpServletRequest.class);
        assertRateLimited("login", AuthController.LoginReq.class, jakarta.servlet.http.HttpServletRequest.class);
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
        AdminPermissionService service = new AdminPermissionService("", mapperWithoutAdminTable(), prodEnvironment());

        assertFalse(service.isLocalOpenMode());
        assertEquals("LOCKED", service.mode());
        assertThrows(BizException.class, () -> service.requireAdmin(10001L));
    }

    @Test
    void devProfileKeepsLocalOpenAdminModeForBootstrap() {
        AdminPermissionService service = new AdminPermissionService("", mapperWithoutAdminTable(), devEnvironment());

        assertEquals("LOCAL_OPEN", service.mode());
        service.requireAdmin(10001L);
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

    private static void assertRateLimited(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AuthController.class.getDeclaredMethod(methodName, parameterTypes);
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        org.junit.jupiter.api.Assertions.assertNotNull(rateLimit, methodName + " must be rate limited");
        org.junit.jupiter.api.Assertions.assertFalse(rateLimit.key().isBlank(), methodName + " rate limit key must not be blank");
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
