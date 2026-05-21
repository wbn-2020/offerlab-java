package com.offerlab.community.infra.web.interceptor;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;
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
        // 非 controller 直接放行（静态资源等）
        if (!(handler instanceof HandlerMethod hm)) {
            return true;
        }

        Method method = hm.getMethod();
        boolean isPublic = method.isAnnotationPresent(PublicApi.class)
                || hm.getBeanType().isAnnotationPresent(PublicApi.class);

        String auth = request.getHeader(HEADER);
        Long uid = null;
        if (StringUtils.hasText(auth) && auth.startsWith(PREFIX)) {
            try {
                uid = jwtService.parseUid(auth.substring(PREFIX.length()));
            } catch (Exception e) {
                log.debug("invalid token: {}", e.getMessage());
            }
        }

        if (uid != null) {
            UserContext.set(uid);
        }

        if (!isPublic && uid == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }

        TraceContext.ensure();
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
        TraceContext.clear();
    }
}
