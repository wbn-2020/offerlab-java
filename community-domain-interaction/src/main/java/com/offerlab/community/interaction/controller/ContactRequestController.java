package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.dto.ContactRequestCreateCmd;
import com.offerlab.community.interaction.api.dto.ContactRequestDTO;
import com.offerlab.community.interaction.api.dto.ContactRequestReportCmd;
import com.offerlab.community.interaction.api.dto.ContactRequestStatsDTO;
import com.offerlab.community.interaction.application.ContactRequestService;
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
@RequestMapping("/api/v1/contact-requests")
@Validated
public class ContactRequestController {

    private final ContactRequestService contactRequestService;

    @PostMapping
    @RateLimit(key = "'contact-request:create:' + #uid", rate = 20, per = 3600)
    public Result<ContactRequestDTO> create(@Valid @RequestBody ContactRequestCreateCmd cmd) {
        return Result.ok(contactRequestService.create(UserContext.require(), cmd));
    }

    @GetMapping("/inbox")
    @RateLimit(key = "'contact-request:inbox:' + #uid", rate = 120, per = 60)
    public Result<PageResult<ContactRequestDTO>> inbox(@RequestParam(required = false) @Size(max = 32) String status,
                                                       @RequestParam(required = false) @Size(max = 64) String cursor,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer limit) {
        return Result.ok(contactRequestService.inbox(UserContext.require(), status, cursor, limit));
    }

    @GetMapping("/outbox")
    @RateLimit(key = "'contact-request:outbox:' + #uid", rate = 120, per = 60)
    public Result<PageResult<ContactRequestDTO>> outbox(@RequestParam(required = false) @Size(max = 32) String status,
                                                        @RequestParam(required = false) @Size(max = 64) String cursor,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(50) Integer limit) {
        return Result.ok(contactRequestService.outbox(UserContext.require(), status, cursor, limit));
    }

    @GetMapping("/stats")
    @RateLimit(key = "'contact-request:stats:' + #uid", rate = 120, per = 60)
    public Result<ContactRequestStatsDTO> stats() {
        return Result.ok(contactRequestService.stats(UserContext.require()));
    }

    @PostMapping("/{id}/accept")
    @RateLimit(key = "'contact-request:handle:' + #uid", rate = 60, per = 60)
    public Result<ContactRequestDTO> accept(@PathVariable @Positive Long id) {
        return Result.ok(contactRequestService.accept(UserContext.require(), id));
    }

    @PostMapping("/{id}/reject")
    @RateLimit(key = "'contact-request:handle:' + #uid", rate = 60, per = 60)
    public Result<ContactRequestDTO> reject(@PathVariable @Positive Long id) {
        return Result.ok(contactRequestService.reject(UserContext.require(), id));
    }

    @PostMapping("/{id}/ignore")
    @RateLimit(key = "'contact-request:handle:' + #uid", rate = 60, per = 60)
    public Result<ContactRequestDTO> ignore(@PathVariable @Positive Long id) {
        return Result.ok(contactRequestService.ignore(UserContext.require(), id));
    }

    @PostMapping("/{id}/report")
    @RateLimit(key = "'contact-request:report:' + #id + ':' + #uid", rate = 10, per = 86400)
    public Result<ContactRequestDTO> report(@PathVariable @Positive Long id,
                                            @Valid @RequestBody(required = false) ContactRequestReportCmd cmd) {
        return Result.ok(contactRequestService.report(UserContext.require(), id, cmd));
    }
}
