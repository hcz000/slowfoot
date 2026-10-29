package org.hcz.work.service.adapter;

import org.hcz.work.entity.WorkType;
import org.hcz.common.exception.AppException;
import org.hcz.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;


// 统一管理作品类型到适配器的映射，避免业务层使用大量 if/else。

@Component
public class WorkContentAdapterRegistry {

    private final Map<WorkType, WorkContentAdapter> adapters;

    public WorkContentAdapterRegistry(List<WorkContentAdapter> adapterList) {
        EnumMap<WorkType, WorkContentAdapter> mapping = new EnumMap<>(WorkType.class);
        for (WorkContentAdapter adapter : adapterList) {
            if (mapping.put(adapter.type(), adapter) != null) {
                throw new IllegalStateException("作品类型适配器重复: " + adapter.type());
            }
        }
        this.adapters = Map.copyOf(mapping);
    }

    public WorkContentAdapter get(WorkType type) {
        WorkContentAdapter adapter = adapters.get(type);
        if (adapter == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "暂不支持该作品类型");
        }
        return adapter;
    }
}
