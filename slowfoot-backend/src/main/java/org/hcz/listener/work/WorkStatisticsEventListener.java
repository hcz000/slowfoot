package org.hcz.listener.work;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.hcz.entity.work.Work;
import org.hcz.event.work.WorkInteractionEvent;
import org.hcz.mapper.work.WorkMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

// 作品统计事件监听器
@Component
public class WorkStatisticsEventListener {

    private final WorkMapper workMapper;

    public WorkStatisticsEventListener(WorkMapper workMapper) {
        this.workMapper = workMapper;
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(WorkInteractionEvent event) {
        if (event.getCountDelta() == 0L) {
            return;
        }

        String column = event.getType().getCountColumn();
        UpdateWrapper<Work> wrapper = new UpdateWrapper<>();
        wrapper.setSql(column + " = GREATEST(COALESCE(" + column + ", 0) + "
                        + event.getCountDelta() + ", 0)")
                .eq("id", event.getWorkId());
        if (workMapper.update(null, wrapper) != 1) {
            throw new IllegalStateException("Work statistics update failed: " + event.getWorkId());
        }
    }
}
