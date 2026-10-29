package org.hcz.file.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;


// 屏蔽具体云存储 SDK 的文件元信息。

@Data
@AllArgsConstructor
public class CloudFileInfo implements Serializable {

    private String contentType;
    private long size;
}
