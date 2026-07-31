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

        if (uid != null && revocationCheckDegraded) {
            if (!isPublic) {
                log.warn("jwt revocation check degraded for protected path: uid={} path={}",
                        LogMask.id(uid), request.getRequestURI());
                throw new BizException(ErrorCode.UNAUTHORIZED);
            }
            log.warn("jwt revocation check degraded for public path, continue anonymously: uid={} path={}",
                    LogMask.id(uid), request.getRequestURI());
            uid = null;
        }

        if (uid != null) {
            UserContext.set(uid);
        }

        if (!isPublic && uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // UserContext/TraceContext 均为线程上下文，必须在请求结束时清理，避免线程复用串号。
        UserContext.clear();
        TraceContext.clear();
    }
}
