package com.offerlab.community.common.exception;

import com.offerlab.community.common.result.ErrorCode;

/**
 * 业务异常
 */
public class BizException extends RuntimeException {
    private final Integer code;
    private final String message;
    private final Object data;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
        this.message = errorCode.getMessage();
        this.data = null;
    }

    public BizException(Integer code, String message) {
        this(code, message, null);
    }

    public BizException(Integer code, String message, Object data) {
        super(message);
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public BizException(String message) {
        super(message);
        this.code = 30000;
        this.message = message;
        this.data = null;
    }

    public Integer getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public Object getData() {
        return data;
    }
}
