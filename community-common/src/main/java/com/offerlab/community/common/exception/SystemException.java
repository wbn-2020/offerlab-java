package com.offerlab.community.common.exception;

/**
 * 系统异常
 */
public class SystemException extends RuntimeException {
    private final Integer code;
    private final String message;

    public SystemException(Integer code, String message) {
        super(message);
        this.code = code;
        this.message = message;
    }

    public SystemException(String message) {
        super(message);
        this.code = 20000;
        this.message = message;
    }

    public SystemException(String message, Throwable cause) {
        super(message, cause);
        this.code = 20000;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
