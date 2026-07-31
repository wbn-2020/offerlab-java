package com.offerlab.community.report.api;

import com.offerlab.community.common.result.PageResult;
import com.offerlab.community.report.api.dto.UserReportReceiptDTO;

public interface UserReportFacade {

    PageResult<UserReportReceiptDTO> listMyReports(Long uid, String sourceType, String status, String cursor, Integer limit);

    UserReportReceiptDTO getMyReport(Long uid, String sourceType, Long reportId);
}
