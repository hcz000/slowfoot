package org.hcz.file.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 完成分片上传的请求体。
 */
@Data
public class MultipartCompleteRequest implements Serializable {

    private String objectKey;
    private String uploadId;
    private List<PartETagInfo> parts;

    @Data
    public static class PartETagInfo implements Serializable {
        private int partNumber;
        private String eTag;
    }
}
