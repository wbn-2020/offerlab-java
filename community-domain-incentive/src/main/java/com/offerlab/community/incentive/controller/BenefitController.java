package com.offerlab.community.incentive.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitOrderCmd;
import com.offerlab.community.incentive.api.IncentiveDtos.BenefitOrderDTO;
import com.offerlab.community.incentive.api.IncentiveDtos.OrderActionCmd;
import com.offerlab.community.incentive.application.BenefitService;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.infra.web.interceptor.PublicApi;
import com.offerlab.community.infra.web.ratelimit.RateLimit;
import jakarta.servlet.http.HttpServletRequest;
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
@RequestMapping("/api/v1/benefits")
@RequiredArgsConstructor
public class BenefitController {
    private final BenefitService benefitService;

    @GetMapping("/catalog")
    @PublicApi
    @RateLimit(key = "'public:benefit-catalog:' + #request.remoteAddr", rate = 120, per = 60, failOpen = false)
    public Result<PageResult<BenefitDTO>> catalog(@RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "20") Integer size,
                                                   HttpServletRequest request) {
        return Result.ok(benefitService.catalog(page, size));
    }

    @PostMapping("/orders")
    @RateLimit(key = "'incentive:benefit-order:' + #uid", rate = 5, per = 300, failOpen = false)
    public Result<BenefitOrderDTO> placeOrder(@Valid @RequestBody BenefitOrderCmd cmd) {
        return Result.ok(benefitService.placeOrder(cmd, UserContext.require()));
    }

    @GetMapping("/orders/status")
    public Result<BenefitOrderDTO> orderStatus(@RequestParam String idempotencyKey) {
        return Result.ok(benefitService.orderByIdempotency(UserContext.require(), idempotencyKey));
    }

    @PostMapping("/orders/{orderId}/cancel")
    @RateLimit(key = "'incentive:benefit-cancel:' + #uid", rate = 10, per = 300, failOpen = false)
    public Result<BenefitOrderDTO> cancelOrder(@PathVariable Long orderId,
                                                @Valid @RequestBody OrderActionCmd cmd) {
        return Result.ok(benefitService.cancelOrder(orderId, cmd, UserContext.require()));
    }
}
