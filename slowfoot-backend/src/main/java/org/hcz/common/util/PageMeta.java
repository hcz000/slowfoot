package org.hcz.common.util;


// Metadata shared by paginated API responses.

public record PageMeta(long page, long size, long total) {
}
