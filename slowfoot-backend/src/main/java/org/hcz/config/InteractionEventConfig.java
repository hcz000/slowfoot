package org.hcz.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;



@Configuration
public class InteractionEventConfig {

    @Bean("notificationEventExecutor")
    public ThreadPoolTaskExecutor notificationEventExecutor() {
        return buildExecutor("notification-event-");
    }


// 默认线程池，供没有显式指定线程池的 @Async 方法使用。

    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        return buildExecutor("default-event-");
    }

    @Bean("recommendationEventExecutor")
    public ThreadPoolTaskExecutor recommendationEventExecutor() {
        return buildExecutor("recommendation-event-");
    }


// 作品 / 评论两条审核链路共用。容量比另一个池大：视频文件审核内部有
// Thread.sleep 轮询（最多约 120s），8 个线程就能占满旧上限；作品审核虽不轮询，但 50 张图片是
// 50 次串行往返，同样可能占住线程数十秒。

    @Bean("auditEventExecutor")
    public ThreadPoolTaskExecutor auditEventExecutor() {
        return buildExecutor("audit-event-", 4, 16);
    }

    private ThreadPoolTaskExecutor buildExecutor(String threadNamePrefix) {
        return buildExecutor(threadNamePrefix, 2, 8);
    }

    private ThreadPoolTaskExecutor buildExecutor(String threadNamePrefix, int corePoolSize, int maxPoolSize) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        // 保持 CallerRunsPolicy + 大队列：队列打满时会由调度器线程内联执行审核，
        // 所以 spring.task.scheduling.pool.size 不能小于 4，队列也不宜缩小。
        executor.setQueueCapacity(1000);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
