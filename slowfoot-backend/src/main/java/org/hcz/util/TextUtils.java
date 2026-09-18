package org.hcz.util;


// 文本长度辅助：把用户可见信息收敛到数据库列长度以内。

public final class TextUtils {

    private TextUtils() {
    }


// 截断到 maxLength 个 UTF-16 码 unit，null 原样返回。
// MySQL utf8mb4 的 VARCHAR(N) 按字符计数，而一个 码 point 至少占一个 码 unit，
// 因此截断到 maxLength 个 码 unit 必然不超过 maxLength 个字符。
// 若切断位置落在代理对中间，则丢弃该半个字符，避免写入孤立的 high surrogate。

    public static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int end = Math.max(0, maxLength);
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }
}
