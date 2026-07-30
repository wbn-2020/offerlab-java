package com.offerlab.community.infra.security;

import com.offerlab.community.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminPermissionServiceTest {
    private static final String LOCAL_OPEN_TOKEN = "test-local-open-token";

    @AfterEach
    void resetRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void localOpenBootstrapRequiresLoopbackRequestInLocalProfiles() {
        AdminPermissionService service = service("local");

        assertFalse(service.isLocalOpenMode(), "local must not allow local-open without request context");
        assertEquals("LOCKED", service.mode(), "local must report locked without request context");
        assertThrows(BizException.class, () -> service.requireAdmin(10001L));

        bindRequest("203.0.113.10");
        assertFalse(service.isLocalOpenMode(), "local must not allow local-open from remote clients");
        assertEquals("LOCKED", service.mode(), "local must report locked for remote clients");
        assertThrows(BizException.class, () -> service.requireAdmin(10001L));

        bindRequest("127.0.0.1");
        assertTrue(service.isLocalOpenMode(), "local may allow local-open from loopback clients with token");
        assertEquals("LOCAL_OPEN", service.mode(), "local must report local-open for loopback clients with token");
        assertDoesNotThrow(() -> service.requireAdmin(10001L));

        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void localOpenBootstrapRequiresConfiguredToken() {
        AdminPermissionService service = service("local");

        bindRequest("127.0.0.1", "wrong-token");

        assertFalse(service.isLocalOpenMode());
        assertEquals("LOCKED", service.mode());
        assertThrows(BizException.class, () -> service.requireAdmin(10001L));
    }

    @Test
    void localOpenBootstrapDoesNotGrantScopeToRemoteClients() {
        AdminPermissionService service = service("local");

        bindRequest("198.51.100.42");

        assertThrows(BizException.class,
                () -> service.requireScope(10001L, AdminPermissionService.ROLE_OPS));
    }

    @Test
    void prodProfileRejectsLocalOpenEvenForLoopbackRequest() {
        AdminPermissionService service = service("prod");

        bindRequest("127.0.0.1");

        assertFalse(service.isLocalOpenMode());
        assertEquals("LOCKED", service.mode());
        assertThrows(BizException.class, () -> service.requireAdmin(10001L));
    }

    private static AdminPermissionService service(String profile) {
        return new AdminPermissionService("", true, LOCAL_OPEN_TOKEN, mapperWithoutAdminTable(), profiles(profile));
    }

    private static Environment profiles(String... activeProfiles) {
        return (Environment) Proxy.newProxyInstance(
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

    private static void bindRequest(String remoteAddr) {
        bindRequest(remoteAddr, LOCAL_OPEN_TOKEN);
    }

    private static void bindRequest(String remoteAddr, String localOpenToken) {
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class[]{HttpServletRequest.class},
                (proxy, method, args) -> requestValue(method, args, remoteAddr, localOpenToken));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static Object requestValue(Method method, Object[] args, String remoteAddr, String localOpenToken) {
        return switch (method.getName()) {
            case "getRemoteAddr" -> remoteAddr;
            case "getMethod" -> "GET";
            case "getRequestURI" -> "/admin";
            case "getContextPath", "getServletPath" -> "";
            case "getLocale" -> Locale.getDefault();
            case "getLocales" -> Collections.enumeration(List.of(Locale.getDefault()));
            case "getHeader" -> "X-OfferLab-Local-Open-Token".equals(args == null ? null : args[0]) ? localOpenToken : null;
            case "getAttribute", "getSession" -> null;
            case "getAttributeNames", "getHeaderNames", "getParameterNames" -> Collections.emptyEnumeration();
            case "getParameterMap" -> Map.of();
            case "setAttribute", "removeAttribute" -> null;
            default -> defaultValue(method.getReturnType());
        };
    }

    private static Object defaultValue(Class<?> returnType) {
        if (!returnType.isPrimitive() || returnType == Void.TYPE) {
            return null;
        }
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == int.class || returnType == short.class || returnType == byte.class) {
            return 0;
        }
        if (returnType == long.class) {
            return 0L;
        }
        if (returnType == float.class) {
            return 0F;
        }
        if (returnType == double.class) {
            return 0D;
        }
        if (returnType == char.class) {
            return '\0';
        }
        return null;
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
            public List<Long> lockEnabledAdminUids() {
                return List.of();
            }

            @Override
            public int countAdminRows() {
                return 0;
            }

            @Override
            public List<Map<String, Object>> listAdmins(int limit) {
                return List.of();
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
}
