package com.offerlab.community.infra.redis.lua;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 启动时扫描 classpath:lua/*.lua 并按文件名缓存
 */
@Slf4j
@Component
public class LuaScriptLoader {

    private final Map<String, RedisScript<Long>> scripts = new HashMap<>();

    @PostConstruct
    public void init() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:lua/*.lua");
            for (Resource r : resources) {
                String filename = r.getFilename();
                if (filename == null) continue;
                String name = filename.replace(".lua", "");
                String content;
                try (var in = r.getInputStream()) {
                    content = new String(StreamUtils.copyToByteArray(in), StandardCharsets.UTF_8);
                }
                DefaultRedisScript<Long> script = new DefaultRedisScript<>();
                script.setScriptText(content);
                script.setResultType(Long.class);
                scripts.put(name, script);
                log.info("loaded lua script: {}", name);
            }
        } catch (Exception e) {
            log.warn("failed to load lua scripts (ok if directory not exist yet): {}", e.getMessage());
        }
    }

    public RedisScript<Long> get(String name) {
        RedisScript<Long> s = scripts.get(name);
        if (s == null) throw new IllegalArgumentException("lua script not found: " + name);
        return s;
    }
}
