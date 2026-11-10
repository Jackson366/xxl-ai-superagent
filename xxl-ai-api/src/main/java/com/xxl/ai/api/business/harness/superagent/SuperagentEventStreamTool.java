package com.xxl.ai.api.business.harness.superagent;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongFunction;
import java.util.function.ToLongFunction;

/**
 * 持久化执行事件的 SSE 传输；按游标补发，完成和断开时释放扫描任务
 *
 * @author Codex 2026-11-11
 */
@Component
public class SuperagentEventStreamTool {
    private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "superagent-events");
        thread.setDaemon(true);
        return thread;
    });

    /* 数据读取与鉴权由业务回调负责，运行时只传输事件 */
    public <T> SseEmitter open(long afterId, LongFunction<List<T>> read, ToLongFunction<T> id) {
        SseEmitter emitter = new SseEmitter(60000L);
        AtomicReference<ScheduledFuture<?>> future = new AtomicReference<>();
        Runnable cancel = () -> {
            ScheduledFuture<?> task = future.get();
            if (task != null) {
                task.cancel(false);
            }
        };
        emitter.onCompletion(cancel);
        emitter.onTimeout(() -> { cancel.run(); emitter.complete(); });
        emitter.onError(error -> cancel.run());
        long[] cursor = {Math.max(0, afterId)};
        future.set(executor.scheduleWithFixedDelay(() -> {
            try {
                List<T> events = read.apply(cursor[0]);
                for (T event : events) {
                    emitter.send(SseEmitter.event().name("event").id(String.valueOf(id.applyAsLong(event))).data(event));
                    cursor[0] = id.applyAsLong(event);
                }
                emitter.send(SseEmitter.event().name("ping").data(cursor[0]));
            } catch (Exception error) {
                cancel.run();
                emitter.completeWithError(error);
            }
        }, 100, 1000, TimeUnit.MILLISECONDS));
        return emitter;
    }

    @PreDestroy
    public void stop() {
        executor.shutdownNow();
    }
}
