package org.hcz.entity.vo;


// 结果 returned after  video share 是 recorded.

public record ShareResult(Long workId, String shareUrl, boolean firstShare) {
}
