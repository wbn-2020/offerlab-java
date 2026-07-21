package com.offerlab.community.user.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.user.api.UserSubscriptionPreferenceFacade;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceDTO;
import com.offerlab.community.user.api.dto.UserSubscriptionPreferenceUpdateCmd;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;

@RestController
@RequestMapping("/api/v1/users/me/relationships")
@RequiredArgsConstructor
@Validated
public class UserSubscriptionPreferenceController {

    private final UserSubscriptionPreferenceFacade preferenceFacade;

    @GetMapping("/{sourceType}/{sourceId}/preference")
    @RateLimit(key = "'user:relationship-preference:get:' + #uid + ':' + #sourceType + ':' + #sourceId",
            rate = 120, per = 60, failOpen = false)
    public Result<UserSubscriptionPreferenceDTO> get(
            @PathVariable @Size(max = 24) String sourceType,
            @PathVariable @Positive Long sourceId) {
        return Result.ok(preferenceFacade.get(UserContext.require(), sourceType, sourceId));
    }

    @PutMapping("/{sourceType}/{sourceId}/preference")
    @RateLimit(key = "'user:relationship-preference:update:' + #uid + ':' + #sourceType + ':' + #sourceId",
            rate = 30, per = 60, failOpen = false)
    public Result<UserSubscriptionPreferenceDTO> update(
            @PathVariable @Size(max = 24) String sourceType,
            @PathVariable @Positive Long sourceId,
            @Valid @RequestBody UserSubscriptionPreferenceUpdateCmd cmd) {
        return Result.ok(preferenceFacade.upsert(
                UserContext.require(),
                sourceType,
                sourceId,
                cmd.getDeliveryMode(),
                cmd.getExpiresAt() == null
                        ? null
                        : cmd.getExpiresAt().atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()));
    }

    @DeleteMapping("/{sourceType}/{sourceId}/preference")
    @RateLimit(key = "'user:relationship-preference:delete:' + #uid + ':' + #sourceType + ':' + #sourceId",
            rate = 30, per = 60, failOpen = false)
    public Result<Void> delete(
            @PathVariable @Size(max = 24) String sourceType,
            @PathVariable @Positive Long sourceId) {
        preferenceFacade.delete(UserContext.require(), sourceType, sourceId);
        return Result.ok();
    }
}
