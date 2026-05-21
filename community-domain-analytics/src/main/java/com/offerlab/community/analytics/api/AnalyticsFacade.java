package com.offerlab.community.analytics.api;

import java.util.List;
import java.util.Map;

/**
 * MVP 阶段：占位实现
 */
public interface AnalyticsFacade {

    void track(Map<String, Object> event);

    List<Map<String, Object>> getHotPosts(int size);

    Map<String, Object> getTrendDashboard(String range);

    Map<String, Object> getPersonalDashboard(Long uid);
}
