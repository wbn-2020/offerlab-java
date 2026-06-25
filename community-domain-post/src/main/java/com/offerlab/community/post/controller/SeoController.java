package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.post.api.dto.SeoLinkDTO;
import com.offerlab.community.post.application.PublicSeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

@RestController
@PublicApi
@RequestMapping("/api/v1/seo")
@RequiredArgsConstructor
public class SeoController {

    private final PublicSeoService publicSeoService;

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public String sitemap() {
        return publicSeoService.buildSitemapXml(currentBaseUrl());
    }

    @GetMapping("/public-links")
    public Result<List<SeoLinkDTO>> publicLinks() {
        return Result.ok(publicSeoService.listPublicLinks(currentBaseUrl()));
    }

    private String currentBaseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .replacePath(null)
                .build()
                .toUriString();
    }
}
