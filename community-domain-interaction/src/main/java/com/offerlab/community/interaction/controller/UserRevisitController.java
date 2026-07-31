package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.dto.RevisitItemDTO;
import com.offerlab.community.interaction.api.dto.RevisitSnoozeCmd;
import com.offerlab.community.interaction.application.UserRevisitService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/me/revisits")
@Validated
public class UserRevisitController {

    private final UserRevisitService revisitService;

    @GetMapping
    @RateLimit(key = "'revisit:list:' + #uid", rate = 60, per = 60)
    public Result<PageResult<RevisitItemDTO>> list(@RequestParam(required = false) @Size(max = 16) String status,
                                                   @RequestParam(required = false) @Size(max = 32) String cursor,
                                                   @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return Result.ok(revisitService.list(UserContext.require(), status, cursor, size));
    }

    @PostMapping("/{itemId}/complete")
    @RateLimit(key = "'revisit:complete:' + #uid", rate = 60, per = 60)
    public Result<RevisitItemDTO> complete(@PathVariable @Positive Long itemId) {
        return Result.ok(revisitService.complete(UserContext.require(), itemId));
    }

    @PostMapping("/{itemId}/snooze")
    @RateLimit(key = "'revisit:snooze:' + #uid", rate = 60, per = 60)
    public Result<RevisitItemDTO> snooze(@PathVariable @Positive Long itemId,
                                         @Valid @RequestBody RevisitSnoozeCmd cmd) {
        return Result.ok(revisitService.snooze(UserContext.require(), itemId, cmd));
    }

    @PostMapping("/{itemId}/ignore")
    @RateLimit(key = "'revisit:ignore:' + #uid", rate = 60, per = 60)
    public Result<RevisitItemDTO> ignore(@PathVariable @Positive Long itemId) {
        return Result.ok(revisitService.ignore(UserContext.require(), itemId));
    }
}
