package org.hcz.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;


// Structured 详情 用于  请求 或 business 错误.

@Getter
@AllArgsConstructor
public class ApiErrorDetail {

    private final String field;
    private final String message;
}
