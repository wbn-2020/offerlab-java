package com.offerlab.community.infra.web.interceptor;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
import com.offerlab.community.common.utils.LogMask;
import com.offerlab.community.infra.security.JwtAuthResult;
import com.offerlab.community.infra.security.JwtService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.trace.TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.lang.reflect.Method;

/**
 * 鉴权拦截器
 * 默认所有 /api/** 接口都要登录，使用 @PublicApi 注解可豁免
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";
    private static final String[] STRICT_REVOCATION_PREFIXES = {
            "/api/v1/admin",
            "/api/v1/expert-certifications/admin",
            "/api/v1/search/admin",
            "/api/v1/operations/admin",
            "/api/v1/ops",
            "/api/v1/posts/admin",
            "/api/v1/tags/admin",
            "/api/v1/comments/admin",
            "/api/v1/users/me",
            "/api/v1/me",
            "/api/v1/notifications",
            "/api/v1/contact-requests",
            "/api/v1/comments/reports",
            "/api/v1/posts/reports",
            "/api/v1/post-drafts",
            "/api/v1/mock-interviews"
    };

    private final JwtService jwtService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 先初始化 traceId，确保鉴权失败等早期异常也能带链路信息返回。
        TraceContext.ensure();

        // 非 controller 直接放行（静态资源等）
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        Method method = hm.getMethod();
        boolean isPublic = method.isAnnotationPresent(PublicApi.class)
                || hm.getBeanType().isAnnotationPresent(PublicApi.class);

        String auth = request.getHeader(HEADER);
        Long uid = null;
        boolean revocationCheckDegraded = false;
        if (StringUtils.hasText(auth) && auth.startsWith(PREFIX)) {
            try {
                String token = auth.substring(PREFIX.length());
                JwtAuthResult authResult = jwtService.parse(token);
                if (authResult != null) {
                    uid = authResult.uid();
                    revocationCheckDegraded = authResult.revocationCheckDegraded();
                } else {
                    uid = jwtService.parseUid(token);
                }
            } catch (Exception e) {
                // 公共接口允许无登录访问，非法 token 按匿名处理；受保护接口后续统一抛 401。
                log.debug("invalid token: {}", e.getMessage());
            }
        }

        if (uid != null && revocationCheckDegraded && requiresStrictRevocation(request)) {
            log.warn("jwt revocation check degraded for sensitive path: uid={} path={}",
                    LogMask.id(uid), request.getRequestURI());
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        if (uid != null) {
            UserContext.set(uid);
        }

        if (!isPublic && uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        return true;
    }

    private static boolean requiresStrictRevocation(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        for (String prefix : STRICT_REVOCATION_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // UserContext/TraceContext 均为线程上下文，必须在请求结束时清理，避免线程复用串号。
        UserContext.clear();
        TraceContext.clear();
    }
}
