package org.hcz.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * 单个分片的预签名上传 URL。
 */
@Data
@AllArgsConstructor
public class MultipartPartUrl implements Serializable {

    private int partNumber;
    private String uploadUrl;
}
