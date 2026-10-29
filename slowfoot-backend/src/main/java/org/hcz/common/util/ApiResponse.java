package org.hcz.common.util;

import lombok.Data;
import org.hcz.common.exception.ApiErrorDetail;
import org.hcz.common.exception.ErrorCode;

import java.text.MessageFormat;
import java.time.Instant;
import java.util.List;


// 统一的接口响应对象。
//
// @param <T> 响应数据的类型

@Data
public class ApiResponse<T> {


// 请求是否成功。

    private Boolean success;


// 面向程序使用的稳定错误码。

    private String errorCode;


// 面向用户展示的提示信息。

    private String message;


// 接口返回的业务数据。

    private T data;


// 分页信息，总条数见 meta.total。

    private PageMeta meta;


// 参数校验或业务错误的详细信息。

    private List<ApiErrorDetail> details;


// 用于串联一次请求的链路 ID。

    private String requestId;


// 响应生成时间。

    private String timestamp;

    public ApiResponse() {
        this.timestamp = Instant.now().toString();
        this.requestId = RequestIdContext.current();
    }


// 创建成功响应。

    public static <T> ApiResponse<T> ok() {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(true);
        response.setErrorCode(ErrorCode.OK.name());
        response.setMessage(ErrorCode.OK.getDefaultMessage());
        return response;
    }


// 创建通用业务失败响应。

    public static <T> ApiResponse<T> error() {
        return error(ErrorCode.BUSINESS_ERROR);
    }


// 根据统一错误码创建失败响应。

    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setSuccess(false);
        response.setErrorCode(errorCode.name());
        response.setMessage(errorCode.getDefaultMessage());
        return response;
    }


// 设置分页信息。

    public ApiResponse<T> page(long page, long size, long total) {
        this.setMeta(new PageMeta(page, size, total));
        return this;
    }


// 设置提示信息。

    public ApiResponse<T> message(String message) {
        this.setMessage(message);
        return this;
    }


// 使用 MessageFormat 设置格式化提示信息。

    public ApiResponse<T> message(String message, Object... objects) {
        this.setMessage(MessageFormat.format(message, objects));
        return this;
    }


// 设置响应数据。

    public ApiResponse<T> data(T result) {
        this.setData(result);
        return this;
    }


// 设置错误详情。

    public ApiResponse<T> details(List<ApiErrorDetail> details) {
        this.setDetails(details);
        return this;
    }
}
