/*
 * 动态线程池（oneThread）基础组件项目
 *
 * 版权所有 (C) [2024-至今] [山东流年网络科技有限公司]
 *
 * 保留所有权利。
 *
 * 1. 定义和解释
 *    本文件（包括其任何修改、更新和衍生内容）是由[山东流年网络科技有限公司]及相关人员开发的。
 *    "软件"指的是与本文件相关的任何代码、脚本、文档和相关的资源。
 *
 * 2. 使用许可
 *    本软件的使用、分发和解释均受中华人民共和国法律的管辖。只有在遵守以下条件的前提下，才允许使用和分发本软件：
 *    a. 未经[山东流年网络科技有限公司]的明确书面许可，不得对本软件进行修改、复制、分发、出售或出租。
 *    b. 任何未授权的复制、分发或修改都将被视为侵犯[山东流年网络科技有限公司]的知识产权。
 *
 * 3. 免责声明
 *    本软件按"原样"提供，没有任何明示或暗示的保证，包括但不限于适销性、特定用途的适用性和非侵权性的保证。
 *    在任何情况下，[山东流年网络科技有限公司]均不对任何直接、间接、偶然、特殊、典型或间接的损害（包括但不限于采购替代商品或服务；使用、数据或利润损失）承担责任。
 *
 * 4. 侵权通知与处理
 *    a. 如果[山东流年网络科技有限公司]发现或收到第三方通知，表明存在可能侵犯其知识产权的行为，公司将采取必要的措施以保护其权利。
 *    b. 对于任何涉嫌侵犯知识产权的行为，[山东流年网络科技有限公司]可能要求侵权方立即停止侵权行为，并采取补救措施，包括但不限于删除侵权内容、停止侵权产品的分发等。
 *    c. 如果侵权行为持续存在或未能得到妥善解决，[山东流年网络科技有限公司]保留采取进一步法律行动的权利，包括但不限于发出警告信、提起民事诉讼或刑事诉讼。
 *
 * 5. 其他条款
 *    a. [山东流年网络科技有限公司]保留随时修改这些条款的权利。
 *    b. 如果您不同意这些条款，请勿使用本软件。
 *
 * 未经[山东流年网络科技有限公司]的明确书面许可，不得使用此文件的任何部分。
 *
 * 本软件受到[山东流年网络科技有限公司]及其许可人的版权保护。
 */

package com.nageoffer.onethread.nacos.cloud.example.test;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 线程池运行时测试用例
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 * 开发时间：2025-05-05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeThreadPoolTest {

    private final ThreadPoolExecutor onethreadProducer;
    private final ThreadPoolExecutor onethreadConsumer;
    private final List<ScheduledExecutorService> burstSchedulers = new ArrayList<>();

    private static final int MAX_TASK = Integer.MAX_VALUE;

    // 使用更安全的线程池构造
    private final ExecutorService simulationExecutor = Executors.newFixedThreadPool(
            2,
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "simulator-thread-" + count.getAndIncrement());
                }
            }
    );

    @PostConstruct
    public void init() {
        startTestSimulation();

        // 启动定时高压段调度
        schedulePeriodicBurstTask(onethreadProducer, "onethreadProducer");
        schedulePeriodicBurstTask(onethreadConsumer, "onethreadConsumer");
    }

    public void startTestSimulation() {
        simulationExecutor.submit(this::simulateHighActiveThreadUsage);
        simulationExecutor.submit(this::simulateQueueUsageHigh);
    }

    /**
     * 模拟活跃线程数占比高的情况
     */
    @SneakyThrows
    private void simulateHighActiveThreadUsage() {
        for (int i = 0; i < MAX_TASK; i++) {
            sleepRandom(1, 1000);
            try {
                onethreadProducer.execute(() -> sleepRandom(200, 1000));
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 模拟阻塞队列占比高的情况
     */
    @SneakyThrows
    private void simulateQueueUsageHigh() {
        for (int i = 0; i < MAX_TASK; i++) {
            sleepRandom(100, 500);
            try {
                onethreadConsumer.execute(() -> sleepRandom(200, 500));
            } catch (Exception ignored) {
            }
        }
    }


    /**
     * 定期突发任务模拟（每1~2小时启动1分钟的高频提交）
     */
    private void schedulePeriodicBurstTask(ThreadPoolExecutor executor, String name) {
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, name + "-burst-scheduler"));
        burstSchedulers.add(scheduler);

        scheduler.scheduleAtFixedRate(() -> {
                    log.info("[突发模式] 开始 {} 的高频提交模拟", name);
                    long end = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(1); // 高压持续1分钟
                    while (System.currentTimeMillis() < end) {
                        try {
                            executor.execute(() -> {
                                try {
                                    TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(10, 100));
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            });
                            // 高频提交（毫秒级）
                            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(5, 20));
                        } catch (RejectedExecutionException e) {
                            log.warn("[突发模式] 任务被拒绝: {}", name);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    log.info("[突发模式] 结束 {} 的高频提交模拟", name);
                },
                ThreadLocalRandom.current().nextLong(10, 20), // 初次延迟（小时）
                ThreadLocalRandom.current().nextLong(60, 120), // 间隔周期（小时）
                TimeUnit.MINUTES
        );
    }

    private void sleepRandom(int minMillis, int maxMillis) {
        try {
            TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(minMillis, maxMillis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("模拟线程被中断，当前线程：{}", Thread.currentThread().getName(), e);
        }
    }
}
