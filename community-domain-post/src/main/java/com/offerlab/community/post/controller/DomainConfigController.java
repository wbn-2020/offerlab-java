package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.post.api.dto.DomainConfigDTO;
import com.offerlab.community.post.api.dto.DomainConfigUpdateCmd;
import com.offerlab.community.post.application.DomainConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DomainConfigController {

    private final DomainConfigService domainConfigService;

    @PublicApi
    @GetMapping("/domains")
    public Result<List<DomainConfigDTO>> listPublic() {
        return Result.ok(domainConfigService.listPublic());
    }

    @GetMapping("/admin/domains")
    public Result<List<DomainConfigDTO>> listAdmin() {
        return Result.ok(domainConfigService.listAdmin());
    }

    @PutMapping("/admin/domains/{domain}")
    public Result<DomainConfigDTO> update(@PathVariable Integer domain,
                                          @Valid @RequestBody DomainConfigUpdateCmd cmd) {
        return Result.ok(domainConfigService.updateDomainConfig(domain, cmd, UserContext.require()));
    }
}
