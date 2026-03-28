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

package com.nageoffer.onethread.nacos.cloud.example.controller;

import com.nageoffer.onethread.core.executor.support.ResizableCapacityLinkedBlockingQueue;
import com.nageoffer.onethread.core.toolkit.ThreadFactoryBuilder;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 线程池性能测试控制器
 * <p>
 * 该类用于对比普通线程池和动态线程池（如onethreadProducer）在不同负载下的性能表现
 * 支持轻负载、中负载和高负载三种测试场景，通过API端点触发
 * <p>
 * 主要指标包括：总耗时、TPS（每秒事务数）和平均延迟
 * 测试流程包括预热、执行测试、收集指标和输出对比结果
 * <p>
 * 📋 流程概览：
 * <ul>
 *     <li>打印测试参数：输出任务数、并发度、单任务耗时等基本信息，方便区分不同测试场景</li>
 *     <li>线程池预热：调用 warmUp() 方法预热两个线程池，让线程池提前创建线程、触发 JIT 编译，避免冷启动带来的性能波动</li>
 *     <li>系统稳定等待：暂停 3 秒，确保预热任务完全结束</li>
 *     <li>执行普通线程池测试：调用 testThreadPool() 方法，收集性能指标（如成功数、失败数、总耗时、TPS、平均延迟）</li>
 *     <li>等待系统恢复：暂停 5 秒，避免两次测试结果相互干扰</li>
 *     <li>执行动态线程池测试：类似普通线程池测试，收集动态线程池的性能指标</li>
 *     <li>输出性能对比结果：调用 printComparison() 方法，打印耗时、TPS 和延迟的差异，并根据 TPS 损耗比例输出评估结论（✓ 可忽略、⚠ 可接受、✗ 需优化）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ThreadPoolPerformanceTestController {

    /**
     * 动态线程池实例，用于性能对比
     */
    private final ThreadPoolExecutor onethreadProducer;

    /**
     * 普通线程池实例：固定核心和最大线程数为40，队列容量为100000，使用CallerRunsPolicy拒绝策略
     */
    private final ThreadPoolExecutor normalPool = new ThreadPoolExecutor(
            40, 40, 3000L, TimeUnit.SECONDS,
            new ResizableCapacityLinkedBlockingQueue<>(100000),
            new ThreadFactoryBuilder().namePrefix("normal-pool_").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    /**
     * 触发轻负载性能测试
     * <p>
     * 测试参数：任务数=1000，并发数=10，任务耗时=100ms
     *
     * @throws Exception 如果测试过程中发生异常
     */
    @GetMapping("/test/light-load")
    public void testLightLoad() throws Exception {
        System.out.println("\n========== 轻负载测试 ==========");
        comparePerformance(1000, 10, 100);
    }

    /**
     * 触发中负载性能测试
     * <p>
     * 测试参数：任务数=50000，并发数=50，任务耗时=50ms
     *
     * @throws Exception 如果测试过程中发生异常
     */
    @GetMapping("/test/medium-load")
    public void testMediumLoad() throws Exception {
        System.out.println("\n========== 中负载测试 ==========");
        comparePerformance(50000, 50, 50);
    }

    /**
     * 触发高负载性能测试
     * <p>
     * 测试参数：任务数=100000，并发数=100，任务耗时=10ms
     *
     * @throws Exception 如果测试过程中发生异常
     */
    @GetMapping("/test/heavy-load")
    public void testHeavyLoad() throws Exception {
        System.out.println("\n========== 高负载测试 ==========");
        comparePerformance(100000, 100, 10);
    }

    /**
     * 对比两个线程池（普通和动态）的性能
     * <p>
     * 流程：打印参数 -> 预热 -> 测试普通池 -> 等待恢复 -> 测试动态池 -> 输出对比
     *
     * @param taskCount    总任务数
     * @param concurrency  并发提交线程数
     * @param taskDuration 每个任务的执行时间（毫秒）
     * @throws Exception 如果预热、测试或等待过程中发生异常
     */
    private void comparePerformance(int taskCount, int concurrency, long taskDuration)
            throws Exception {

        System.out.println(String.format("测试参数: 任务数=%d, 并发数=%d, 任务耗时=%dms", taskCount, concurrency, taskDuration));

        // 预热阶段：避免冷启动影响
        System.out.println("预热中...");
        warmUp(normalPool, 1000, taskDuration);
        warmUp(onethreadProducer, 1000, taskDuration);
        Thread.sleep(3000);  // 等待系统稳定

        // 测试普通线程池
        System.out.println("\n[1/2] 测试普通线程池...");
        PerformanceMetrics normalMetrics = testThreadPool(
                normalPool, taskCount, concurrency, taskDuration, "普通线程池"
        );

        // 等待恢复，避免测试干扰
        System.out.println("等待系统恢复...");
        Thread.sleep(5000);

        // 测试动态线程池
        System.out.println("\n[2/2] 测试动态线程池...");
        PerformanceMetrics dynamicMetrics = testThreadPool(
                onethreadProducer, taskCount, concurrency, taskDuration, "动态线程池"
        );

        // 输出对比结果
        printComparison(normalMetrics, dynamicMetrics);
    }

    /**
     * 测试单个线程池的性能
     * <p>
     * 使用并发提交线程池（submitter）模拟负载，向目标线程池提交任务
     * 收集指标：成功/失败数、总耗时、TPS、平均延迟
     * 任务通过睡眠模拟耗时
     *
     * @param pool         要测试的线程池
     * @param taskCount    总任务数
     * @param concurrency  并发提交线程数
     * @param taskDuration 每个任务的执行时间（毫秒）
     * @param poolType     线程池类型（用于日志输出）
     * @return 性能指标对象
     * @throws Exception 如果等待超时或终止失败
     */
    private PerformanceMetrics testThreadPool(ThreadPoolExecutor pool, int taskCount, int concurrency, long taskDuration, String poolType)
            throws Exception {
        // 控制并发统一开始和结束
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(taskCount);

        // 统计指标
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicLong totalLatency = new AtomicLong(0);

        // 创建并发提交任务的线程池
        ExecutorService submitter = Executors.newFixedThreadPool(concurrency);

        long startTime = System.currentTimeMillis();

        // 提交所有任务
        for (int i = 0; i < taskCount; i++) {
            final int taskId = i;
            submitter.execute(() -> {
                try {
                    startLatch.await();  // 等待统一开始信号

                    long taskStartTime = System.nanoTime();

                    // 提交到被测试的线程池
                    pool.execute(() -> {
                        try {
                            Thread.sleep(taskDuration);
                            successCount.incrementAndGet();

                            long taskEndTime = System.nanoTime();
                            long latency = (taskEndTime - taskStartTime) / 1_000_000;
                            totalLatency.addAndGet(latency);

                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            failCount.incrementAndGet();
                        } catch (Exception e) {
                            log.error("任务执行失败: {}", e.getMessage());
                            failCount.incrementAndGet();
                        } finally {
                            endLatch.countDown();
                        }
                    });

                } catch (RejectedExecutionException e) {
                    log.warn("任务被拒绝: taskId={}", taskId);
                    failCount.incrementAndGet();
                    endLatch.countDown();
                } catch (Exception e) {
                    log.error("提交任务失败: {}", e.getMessage());
                    failCount.incrementAndGet();
                    endLatch.countDown();
                }
            });
        }

        // 统一开始
        startLatch.countDown();

        // 等待所有任务完成（超时5分钟）
        boolean finished = endLatch.await(5, TimeUnit.MINUTES);

        long endTime = System.currentTimeMillis();

        // 关闭提交线程池
        submitter.shutdown();
        submitter.awaitTermination(10, TimeUnit.SECONDS);

        if (!finished) {
            System.out.println("测试超时！部分任务未完成");
        }

        // 计算指标
        long duration = endTime - startTime;
        double tps = successCount.get() * 1000.0 / duration;
        double avgLatency = successCount.get() > 0 ?
                totalLatency.get() / (double) successCount.get() : 0;

        // 输出结果
        System.out.println(String.format("%s 测试完成:", poolType));
        System.out.println(String.format("  总任务数: %d", taskCount));
        System.out.println(String.format("  成功: %d, 失败: %d", successCount.get(), failCount.get()));
        System.out.println(String.format("  总耗时: %d ms", duration));
        System.out.println(String.format("  TPS: %s req/s", formatDouble(tps)));
        System.out.println(String.format("  平均延迟: %s ms", formatDouble(avgLatency)));

        return PerformanceMetrics.builder()
                .poolType(poolType)
                .taskCount(taskCount)
                .successCount(successCount.get())
                .failCount(failCount.get())
                .duration(duration)
                .tps(tps)
                .avgLatency(avgLatency)
                .build();
    }

    /**
     * 预热线程池：提交指定数量的任务，让线程池初始化线程和触发JIT优化
     *
     * @param pool         要预热的线程池
     * @param count        预热任务数
     * @param taskDuration 每个任务的执行时间（毫秒）
     * @throws InterruptedException 如果等待被中断
     */
    private void warmUp(ThreadPoolExecutor pool, int count, long taskDuration)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            try {
                pool.execute(() -> {
                    try {
                        Thread.sleep(taskDuration);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                });
            } catch (RejectedExecutionException e) {
                latch.countDown();
            }
        }
        latch.await();
    }

    /**
     * 打印两个线程池的性能对比结果
     * <p>
     * 计算并输出耗时、TPS 和延迟的百分比差异，并给出综合评估
     *
     * @param normal  普通线程池指标
     * @param dynamic 动态线程池指标
     */
    private void printComparison(PerformanceMetrics normal, PerformanceMetrics dynamic) {
        double timeOverhead = (dynamic.getDuration() - normal.getDuration()) * 100.0
                / normal.getDuration();
        double tpsOverhead = (normal.getTps() - dynamic.getTps()) * 100.0
                / normal.getTps();
        double latencyOverhead = (dynamic.getAvgLatency() - normal.getAvgLatency()) * 100.0
                / normal.getAvgLatency();

        System.out.println("\n========================================");
        System.out.println("\n           性能对比结果");
        System.out.println("\n========================================");

        System.out.println("\n【耗时对比】");
        System.out.println(String.format("  普通线程池: %d ms", normal.getDuration()));
        System.out.println(String.format("  动态线程池: %d ms", dynamic.getDuration()));
        System.out.println(String.format("  时间损耗: %s", formatPercentage(timeOverhead)));

        System.out.println("\n【TPS 对比】");
        System.out.println(String.format("  普通线程池: %s req/s", formatDouble(normal.getTps())));
        System.out.println(String.format("  动态线程池: %s req/s", formatDouble(dynamic.getTps())));
        System.out.println(String.format("  TPS 下降: %s", formatPercentage(tpsOverhead)));

        System.out.println("\n【延迟对比】");
        System.out.println(String.format("  普通线程池: %s ms", formatDouble(normal.getAvgLatency())));
        System.out.println(String.format("  动态线程池: %s ms", formatDouble(dynamic.getAvgLatency())));
        System.out.println(String.format("  延迟增加: %s", formatPercentage(latencyOverhead)));

        System.out.println("\n【综合评估】");
        if (tpsOverhead < 5) {
            System.out.println("  ✓ 性能损耗很小，可以忽略");
        } else if (tpsOverhead < 10) {
            System.out.println("  ⚠ 性能损耗可接受，需要权衡");
        } else {
            System.out.println("  ✗ 性能损耗较大，需要优化");
        }

        System.out.println("\n========================================");
    }

    /**
     * 格式化百分比值，保留两位小数，并添加正负号
     *
     * @param value 要格式化的值
     * @return 格式化后的字符串（如 "+5.00%" 或 "-3.50%"）
     */
    private String formatPercentage(double value) {
        String sign = value > 0 ? "+" : "";
        return String.format("%s%.2f%%", sign, value);
    }

    /**
     * 格式化双精度浮点数，保留两位小数
     *
     * @param value 要格式化的值
     * @return 格式化后的字符串（如 "12.34"）
     */
    private String formatDouble(double value) {
        return String.format("%.2f", value);
    }

    /**
     * 性能指标数据类，用于存储测试结果
     */
    @Data
    @Builder
    static class PerformanceMetrics {
        private String poolType;
        private Integer taskCount;
        private Integer successCount;
        private Integer failCount;
        private Long duration;
        private Double tps;
        private Double avgLatency;
    }
}
