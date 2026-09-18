package org.hcz.exception;


// Raised 当  authenticated 用户 lacks 该 required permission.

public class AuthorityException extends AppException {

    public AuthorityException(String msg) {
        super(ErrorCode.FORBIDDEN, msg);
    }
}
