package com.offerlab.community.common.utils;

import com.offerlab.community.common.exception.BizException;
import com.offerlab.community.common.result.ErrorCode;

public final class RiskConfirmation {
    public static final String CONFIRM_PHRASE = "CONFIRM";

    private RiskConfirmation() {
    }

    public static String requireHigh(String remark) {
        String cleanRemark = cleanRemark(remark);
        if (cleanRemark == null) {
            throw paramError("risk confirmation remark is required");
        }
        return cleanRemark;
    }

    public static String requireCritical(String remark, String confirmationPhrase) {
        String cleanRemark = requireHigh(remark);
        if (!CONFIRM_PHRASE.equals(clean(confirmationPhrase))) {
            throw paramError("risk confirmation phrase CONFIRM is required");
        }
        return cleanRemark;
    }

    public static String cleanRemark(String remark) {
        String value = clean(remark);
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    private static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BizException paramError(String message) {
        return new BizException(ErrorCode.PARAM_ERROR.getCode(), message);
    }
}
