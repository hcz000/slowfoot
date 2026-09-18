package org.hcz.exception;


public class LimiterException extends AppException {


    public LimiterException(String msg) {
        super(ErrorCode.RATE_LIMITED, msg);
    }
}
