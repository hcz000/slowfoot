package org.hcz.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;


// Stable machine-readable 错误 codes 用于 by 该 HTTP API.

@Getter
public enum ErrorCode {

    OK(HttpStatus.OK, "成功"),
    BUSINESS_ERROR(HttpStatus.BAD_REQUEST, "请求无法完成"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "请求参数校验失败"),
    CAPTCHA_INVALID(HttpStatus.BAD_REQUEST, "验证码不正确"),
    INVALID_AUDIT_STATUS(HttpStatus.BAD_REQUEST, "审核状态不合法"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "请先登录"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "账号或密码错误"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "权限不足"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "资源不存在"),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "评论不存在"),
    WORK_NOT_FOUND(HttpStatus.NOT_FOUND, "作品不存在"),
    CONFLICT(HttpStatus.CONFLICT, "资源状态冲突"),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "资源已存在"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    DEPENDENCY_FAILURE(HttpStatus.BAD_GATEWAY, "依赖服务暂时不可用"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}
