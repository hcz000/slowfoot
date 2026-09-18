package org.hcz.schedul;

import org.hcz.entity.vo.WorkRankItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;


// 使用最小堆在 JVM 内筛选热度最高的 K 个视频。

public class TopKSelector {

    private final int k;
    private final Queue<WorkRankItem> queue;

    public TopKSelector(int k, Queue<WorkRankItem> queue) {
        this.k = k;
        this.queue = queue;
    }


// 新视频热度更高时，替换当前堆中热度最低的视频。

    public void add(WorkRankItem hotVideo) {
        if (queue.size() < k) {
            queue.add(hotVideo);
        } else if (queue.peek().getHot() < hotVideo.getHot()) {
            queue.poll();
            queue.add(hotVideo);
        }
    }


// 取出全部候选结果，并按热度从高到低排序。

    public List<WorkRankItem> get() {
        final ArrayList<WorkRankItem> list = new ArrayList<>(queue.size());
        while (!queue.isEmpty()) {
            list.add(queue.poll());
        }
        Collections.reverse(list);
        return list;
    }
}
