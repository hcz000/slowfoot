package org.hcz.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 分片上传初始化结果。
 */
@Data
@AllArgsConstructor
public class MultipartInitResult implements Serializable {

    private String uploadId;
    private String objectKey;
}
