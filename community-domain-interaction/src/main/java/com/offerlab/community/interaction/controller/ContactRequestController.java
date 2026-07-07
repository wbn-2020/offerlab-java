package com.offerlab.community.interaction.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import com.offerlab.community.interaction.api.dto.ContactRequestCreateCmd;
import com.offerlab.community.interaction.api.dto.ContactRequestDTO;
import com.offerlab.community.interaction.application.ContactRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
public class ContactRequestController {

    private final ContactRequestService contactRequestService;

    @PostMapping
    @RateLimit(key = "'contact-request:create:' + #uid", rate = 20, per = 3600)
    public Result<ContactRequestDTO> create(@Valid @RequestBody ContactRequestCreateCmd cmd) {
        return Result.ok(contactRequestService.create(UserContext.require(), cmd));
    }

    @GetMapping("/inbox")
    public Result<PageResult<ContactRequestDTO>> inbox(@RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String cursor,
                                                       @RequestParam(defaultValue = "20") Integer limit) {
        return Result.ok(contactRequestService.inbox(UserContext.require(), status, cursor, limit));
    }

    @GetMapping("/outbox")
    public Result<PageResult<ContactRequestDTO>> outbox(@RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String cursor,
                                                        @RequestParam(defaultValue = "20") Integer limit) {
        return Result.ok(contactRequestService.outbox(UserContext.require(), status, cursor, limit));
    }

    @PostMapping("/{id}/accept")
    @RateLimit(key = "'contact-request:handle:' + #uid", rate = 60, per = 60)
    public Result<ContactRequestDTO> accept(@PathVariable Long id) {
        return Result.ok(contactRequestService.accept(UserContext.require(), id));
    }

    @PostMapping("/{id}/reject")
    @RateLimit(key = "'contact-request:handle:' + #uid", rate = 60, per = 60)
    public Result<ContactRequestDTO> reject(@PathVariable Long id) {
        return Result.ok(contactRequestService.reject(UserContext.require(), id));
    }

    @PostMapping("/{id}/ignore")
    @RateLimit(key = "'contact-request:handle:' + #uid", rate = 60, per = 60)
    public Result<ContactRequestDTO> ignore(@PathVariable Long id) {
        return Result.ok(contactRequestService.ignore(UserContext.require(), id));
    }

    @PostMapping("/{id}/report")
    @RateLimit(key = "'contact-request:report:' + #id + ':' + #uid", rate = 10, per = 86400)
    public Result<ContactRequestDTO> report(@PathVariable Long id) {
        return Result.ok(contactRequestService.report(UserContext.require(), id));
    }
}
