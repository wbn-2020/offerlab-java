package com.offerlab.community.report.controller;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.common.result.Result;
import com.offerlab.community.infra.security.UserContext;
import com.offerlab.community.report.api.UserReportFacade;
import com.offerlab.community.report.api.dto.UserReportReceiptDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/reports")
public class UserReportController {

    private final UserReportFacade userReportFacade;

    public UserReportController(UserReportFacade userReportFacade) {
        this.userReportFacade = userReportFacade;
    }

    @GetMapping
    public Result<PageResult<UserReportReceiptDTO>> listMyReports(@RequestParam(required = false) String sourceType,
                                                                  @RequestParam(required = false) String status,
                                                                  @RequestParam(required = false) String cursor,
                                                                  @RequestParam(required = false) Integer limit) {
        return Result.ok(userReportFacade.listMyReports(UserContext.require(), sourceType, status, cursor, limit));
    }

    @GetMapping("/{sourceType}/{reportId}")
    public Result<UserReportReceiptDTO> getMyReport(@PathVariable String sourceType,
                                                    @PathVariable Long reportId) {
        return Result.ok(userReportFacade.getMyReport(UserContext.require(), sourceType, reportId));
    }
}
