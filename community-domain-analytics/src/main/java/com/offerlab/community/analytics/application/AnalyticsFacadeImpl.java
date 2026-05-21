package com.offerlab.community.analytics.application;

import com.offerlab.community.analytics.api.AnalyticsFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * MVP 占位
 */
@Slf4j
@Service
public class AnalyticsFacadeImpl implements AnalyticsFacade {

    @Override
    public void track(Map<String, Object> event) {
        log.debug("track: {}", event);
    }

    @Override
    public List<Map<String, Object>> getHotPosts(int size) {
        return List.of();
    }

    @Override
    public Map<String, Object> getTrendDashboard(String range) {
        return Map.of("range", range == null ? "30d" : range, "items", List.of());
    }

    @Override
    public Map<String, Object> getPersonalDashboard(Long uid) {
        return Map.of("uid", uid, "items", List.of());
    }
}
