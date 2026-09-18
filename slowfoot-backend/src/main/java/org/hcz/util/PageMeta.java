package org.hcz.util;


// Metadata shared by paginated API responses.

public record PageMeta(long page, long size, long total) {
}
