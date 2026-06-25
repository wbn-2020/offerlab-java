package com.offerlab.community.post.controller;

import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.post.api.dto.ExpertCertificationApplicationDTO;
import com.offerlab.community.post.api.dto.ExpertCertificationApplyCmd;
import com.offerlab.community.post.api.dto.ExpertCertificationEligibilityDTO;
import com.offerlab.community.post.api.dto.ExpertCertificationReviewCmd;
import com.offerlab.community.post.application.ExpertCertificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/expert-certifications")
@RequiredArgsConstructor
public class ExpertCertificationController {

    private final ExpertCertificationService expertCertificationService;

    @GetMapping("/eligibility")
    public Result<ExpertCertificationEligibilityDTO> eligibility(@RequestParam Integer domain) {
        return Result.ok(expertCertificationService.getEligibility(UserContext.require(), domain));
    }

    @GetMapping("/applications/me")
    public Result<List<ExpertCertificationApplicationDTO>> listMine(@RequestParam(required = false) Integer domain) {
        return Result.ok(expertCertificationService.listMine(UserContext.require(), domain));
    }

    @PostMapping("/applications")
    public Result<ExpertCertificationApplicationDTO> submit(@Valid @RequestBody ExpertCertificationApplyCmd cmd) {
        return Result.ok(expertCertificationService.submit(cmd, UserContext.require()));
    }

    @PostMapping("/applications/{applicationId}/revoke")
    public Result<ExpertCertificationApplicationDTO> revoke(@PathVariable Long applicationId,
                                                            @Valid @RequestBody RevokeReq req) {
        return Result.ok(expertCertificationService.revoke(applicationId, req.getNote(), UserContext.require()));
    }

    @GetMapping("/admin/applications")
    public Result<List<ExpertCertificationApplicationDTO>> reviewQueue(@RequestParam Integer domain,
                                                                       @RequestParam(required = false) Integer status,
                                                                       @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(expertCertificationService.listReviewQueue(domain, status, limit, UserContext.require()));
    }

    @PostMapping("/admin/applications/{applicationId}/review")
    public Result<ExpertCertificationApplicationDTO> review(@PathVariable Long applicationId,
                                                            @Valid @RequestBody ExpertCertificationReviewCmd cmd) {
        return Result.ok(expertCertificationService.review(applicationId, cmd, UserContext.require()));
    }

    @Data
    public static class RevokeReq {
        @Size(max = 500)
        private String note;
    }
}
