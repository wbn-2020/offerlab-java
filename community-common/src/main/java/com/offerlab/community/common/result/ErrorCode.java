package com.offerlab.community.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 成功
    SUCCESS(0, "success"),

    // 客户端错误 1xxxx
    PARAM_ERROR(10001, "参数错误"),
    INVALID_REQUEST(10002, "请求格式错误"),
    RESOURCE_NOT_FOUND(10404, "资源不存在"),
    UNAUTHORIZED(10401, "未登录"),
    FORBIDDEN(10403, "无权限"),
    RATE_LIMIT_EXCEEDED(10429, "请求过于频繁"),

    // 业务错误 3xxxx
    DUPLICATE_OPERATION(30001, "重复操作"),
    INVALID_STATUS(30002, "状态不合法"),
    USER_NOT_FOUND(30101, "用户不存在"),
    USER_ALREADY_EXISTS(30102, "用户已存在"),
    PASSWORD_ERROR(30103, "密码错误"),
    POST_NOT_FOUND(30201, "帖子不存在"),
    POST_DELETED(30202, "帖子已删除"),
    COMMENT_NOT_FOUND(30301, "评论不存在"),
    FOLLOW_ALREADY_EXISTS(30401, "已关注"),
    FOLLOW_NOT_EXISTS(30402, "未关注"),
    LIKE_ALREADY_EXISTS(30501, "已点赞"),
    LIKE_NOT_EXISTS(30502, "未点赞"),
    FAVORITE_ALREADY_EXISTS(30601, "已收藏"),
    FAVORITE_NOT_EXISTS(30602, "未收藏"),

    // 系统错误 2xxxx
    SYSTEM_ERROR(20000, "系统错误"),
    DATABASE_ERROR(20001, "数据库错误"),
    CACHE_ERROR(20002, "缓存错误"),
    MQ_ERROR(20003, "消息队列错误"),
    ELASTICSEARCH_ERROR(20004, "搜索服务错误"),
    DEPENDENCY_ERROR(20500, "依赖服务异常");

    private final Integer code;
    private final String message;
}
