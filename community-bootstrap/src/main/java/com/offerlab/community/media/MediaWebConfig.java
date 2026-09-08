package com.offerlab.community.media;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 上传图片的只读静态访问：GET /media/** → 本地 upload-dir。
 * 路径由服务端配置，不接受运行时参数；目录遍历由 Spring 资源处理器防护。
 */
@Configuration
public class MediaWebConfig implements WebMvcConfigurer {

    private final String uploadDir;

    public MediaWebConfig(@Value("${offerlab.media.upload-dir:./data/media}") String uploadDir) {
        this.uploadDir = uploadDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        registry.addResourceHandler("/media/**")
                .addResourceLocations(root.toUri().toString())
                .setCachePeriod(86400);
    }
}
