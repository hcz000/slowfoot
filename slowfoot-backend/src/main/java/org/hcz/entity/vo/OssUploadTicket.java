package org.hcz.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;


// 前端直传 OSS 所需的临时上传凭证。

@Data
@AllArgsConstructor
public class OssUploadTicket implements Serializable {

    private String uploadUrl;
    private String objectKey;
    private String contentType;
    private long expiresIn;
    private String method;
    private long maxSizeBytes;
}
