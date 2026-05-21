package com.offerlab.community.infra.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 注入 traceId 到 MDC 与响应头
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String existing = req.getHeader(TraceContext.HEADER);
        String traceId = StringUtils.hasText(existing) ? TraceContext.set(existing) : TraceContext.ensure();
        resp.setHeader(TraceContext.HEADER, traceId);
        try {
            chain.doFilter(req, resp);
        } finally {
            TraceContext.clear();
        }
    }
}
