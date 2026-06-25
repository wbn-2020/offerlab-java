package com.offerlab.community.post.application;

import com.offerlab.community.post.api.dto.SeoLinkDTO;
import com.offerlab.community.post.infrastructure.persistence.mapper.PostMapper;
import com.offerlab.community.post.infrastructure.persistence.po.PostPO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicSeoService {

    private static final String POST_PATH_PREFIX = "/post/";
    private static final int PAGE_SIZE = 500;
    private static final int MAX_PUBLIC_POST_LINKS = 5000;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PostMapper postMapper;

    public List<SeoLinkDTO> listPublicLinks(String baseUrl) {
        List<SeoLinkDTO> links = new ArrayList<>();
        String activeBaseUrl = normalizeBaseUrl(baseUrl);
        links.add(staticLink(activeBaseUrl, "/", "static", null));
        links.add(staticLink(activeBaseUrl, "/explore", "static", null));
        links.add(staticLink(activeBaseUrl, "/about", "static", null));

        long lastId = 0L;
        int scanned = 0;
        while (scanned < MAX_PUBLIC_POST_LINKS) {
            List<PostPO> posts = postMapper.selectPublicSeoPosts(lastId, PAGE_SIZE);
            if (posts == null || posts.isEmpty()) {
                break;
            }
            for (PostPO post : posts) {
                links.add(staticLink(activeBaseUrl, POST_PATH_PREFIX + post.getId(), "post", formatTime(lastModified(post))));
                scanned++;
                lastId = Math.max(lastId, post.getId() == null ? 0L : post.getId());
                if (scanned >= MAX_PUBLIC_POST_LINKS) {
                    break;
                }
            }
            if (posts.size() < PAGE_SIZE) {
                break;
            }
        }
        return links;
    }

    public String buildSitemapXml(String baseUrl) {
        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for (SeoLinkDTO link : listPublicLinks(baseUrl)) {
            xml.append("<url>");
            xml.append("<loc>").append(escapeXml(link.getUrl())).append("</loc>");
            if (StringUtils.hasText(link.getLastModified())) {
                xml.append("<lastmod>").append(escapeXml(link.getLastModified())).append("</lastmod>");
            }
            xml.append("</url>");
        }
        xml.append("</urlset>");
        return xml.toString();
    }

    private SeoLinkDTO staticLink(String baseUrl, String path, String type, String lastModified) {
        return SeoLinkDTO.builder()
                .path(path)
                .url(baseUrl + path)
                .type(type)
                .lastModified(lastModified)
                .build();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "";
        }
        String normalized = baseUrl.trim();
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    private LocalDateTime lastModified(PostPO post) {
        if (post == null) {
            return null;
        }
        return post.getUpdateTime() != null ? post.getUpdateTime() : post.getCreateTime();
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FORMATTER.format(time);
    }

    private String escapeXml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
