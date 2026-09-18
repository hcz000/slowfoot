package org.hcz.entity.vo;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;


// 管理端作品统计。

@Data
public class WorkStatistics {

    private long allCount;

    private long successCount;

    private long processCount;

    private long passCount;


// 按作品类型（VIDEO / IMAGE / TEXT）分别计数。

    private Map<String, Long> byWorkType = new LinkedHashMap<>();
}
