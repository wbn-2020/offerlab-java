package com.offerlab.community.common.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一响应
 * code=0 成功；其他见 ErrorCode
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {

    private Integer code;
    private String message;
    private T data;
    private String traceId;

    public static <T> Result<T> ok(T data) {
        return Result.<T>builder().code(0).message("ok").data(data).build();
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        return Result.<T>builder().code(code).message(message).build();
    }

    public static <T> Result<T> fail(ErrorCode err) {
        return fail(err.getCode(), err.getMessage());
    }

    public boolean isSuccess() {
        return code != null && code == 0;
    }
}
