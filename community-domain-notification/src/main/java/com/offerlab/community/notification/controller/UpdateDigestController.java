package com.offerlab.community.notification.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.notification.api.dto.UpdateDigestItemDTO;
import com.offerlab.community.notification.application.UpdateDigestQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/updates")
@RequiredArgsConstructor
@Validated
public class UpdateDigestController {

    private final UpdateDigestQueryService queryService;

    @GetMapping
    @RateLimit(key = "'update-digest:list:' + #uid", rate = 90, per = 60, failOpen = false)
    public Result<PageResult<UpdateDigestItemDTO>> list(
            @RequestParam(required = false) @Size(max = 24) String sourceType,
            @RequestParam(required = false) @Size(max = 80) String sourceId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Size(max = 64) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(queryService.list(
                UserContext.require(), sourceType, sourceId, unreadOnly, cursor, size));
    }
}
