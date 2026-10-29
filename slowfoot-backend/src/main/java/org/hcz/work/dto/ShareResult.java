package org.hcz.work.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

// 结果 returned after  video share 是 recorded.

public record ShareResult(@JsonSerialize(using = ToStringSerializer.class) Long workId,
                          String shareUrl,
                          boolean firstShare) {
}
