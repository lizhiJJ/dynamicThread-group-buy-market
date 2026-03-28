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

package com.nageoffer.onethread.dashboard.dev.starter.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.net.Ipv4Util;
import cn.hutool.core.util.ReflectUtil;
import com.nageoffer.onethread.core.executor.OneThreadExecutor;
import com.nageoffer.onethread.core.executor.OneThreadRegistry;
import com.nageoffer.onethread.core.executor.ThreadPoolExecutorHolder;
import com.nageoffer.onethread.dashboard.dev.starter.dto.ThreadPoolDashBoardDevBaseMetricsRespDTO;
import com.nageoffer.onethread.dashboard.dev.starter.dto.ThreadPoolDashBoardDevRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.nageoffer.onethread.dashboard.dev.starter.toolkit.MemoryUtil.getFreeMemory;
import static com.nageoffer.onethread.dashboard.dev.starter.toolkit.MemoryUtil.getMemoryProportion;

/**
 * 动态线程池接口
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 * 开发时间：2025-05-17
 */
@Slf4j
public class DynamicThreadPoolService {

    @Value("${server.port:8080}")
    private String port;
    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;

    /**
     * 获取线程池的轻量级运行指标（无锁，适合高频调用）
     *
     * @param threadPoolId 线程池唯一标识
     * @return 线程池简化视图，仅包含关键运行时指标
     */
    public ThreadPoolDashBoardDevBaseMetricsRespDTO getBasicMetrics(String threadPoolId) {
        ThreadPoolExecutorHolder holder = OneThreadRegistry.getHolder(threadPoolId);
        Optional.ofNullable(holder).orElseThrow(() -> new RuntimeException("No thread pool with id " + threadPoolId));

        ThreadPoolExecutor executor = holder.getExecutor();
        int corePoolSize = executor.getCorePoolSize();
        int maximumPoolSize = executor.getMaximumPoolSize();
        long keepAliveTime = executor.getKeepAliveTime(TimeUnit.SECONDS);

        BlockingQueue<?> blockingQueue = executor.getQueue();
        int blockingQueueSize = blockingQueue.size();
        int remainingCapacity = blockingQueue.remainingCapacity();
        int queueCapacity = blockingQueueSize + remainingCapacity;
        String rejectedExecutionHandlerName = executor.getRejectedExecutionHandler().toString();

        long rejectCount = -1L;
        if (executor instanceof OneThreadExecutor) {
            rejectCount = ((OneThreadExecutor) executor).getRejectCount().get();
        }

        return ThreadPoolDashBoardDevBaseMetricsRespDTO.builder()
                .threadPoolId(threadPoolId)
                .corePoolSize(corePoolSize)
                .maximumPoolSize(maximumPoolSize)
                .keepAliveTime(keepAliveTime)
                .workQueueName(blockingQueue.getClass().getSimpleName())
                .workQueueSize(blockingQueueSize)
                .workQueueRemainingCapacity(remainingCapacity)
                .workQueueCapacity(queueCapacity)
                .rejectedHandlerName(rejectedExecutionHandlerName)
                .rejectCount(rejectCount)
                .activeProfile(activeProfile.toUpperCase())
                .networkAddress(Ipv4Util.LOCAL_IP + ":" + port)
                .build();
    }

    /**
     * 获取线程池的完整运行时状态（可能涉及锁操作，不建议高频调用）
     *
     * @param threadPoolId 线程池唯一标识
     * @return 完整的线程池运行状态信息
     */
    public ThreadPoolDashBoardDevRespDTO getRuntimeInfo(String threadPoolId) {
        ThreadPoolExecutorHolder holder = OneThreadRegistry.getHolder(threadPoolId);
        Optional.ofNullable(holder).orElseThrow(() -> new RuntimeException("No thread pool with id " + threadPoolId));

        ThreadPoolExecutor executor = holder.getExecutor();
        BlockingQueue<?> queue = executor.getQueue();

        long rejectCount = -1L;
        if (executor instanceof OneThreadExecutor) {
            rejectCount = ((OneThreadExecutor) executor).getRejectCount().get();
        }

        int workQueueSize = queue.size(); // API 有锁，避免高频率调用
        int remainingCapacity = queue.remainingCapacity(); // API 有锁，避免高频率调用
        return ThreadPoolDashBoardDevRespDTO.builder()
                .threadPoolId(holder.getThreadPoolId())
                .corePoolSize(executor.getCorePoolSize())
                .maximumPoolSize(executor.getMaximumPoolSize())
                .activePoolSize(executor.getActiveCount())  // API 有锁，避免高频率调用
                .currentPoolSize(executor.getPoolSize())  // API 有锁，避免高频率调用
                .completedTaskCount(executor.getCompletedTaskCount())  // API 有锁，避免高频率调用
                .largestPoolSize(executor.getLargestPoolSize())  // API 有锁，避免高频率调用
                .workQueueName(queue.getClass().getSimpleName())
                .workQueueSize(workQueueSize)
                .workQueueRemainingCapacity(remainingCapacity)
                .workQueueCapacity(workQueueSize + remainingCapacity)
                .rejectedHandlerName(executor.getRejectedExecutionHandler().toString())
                .rejectCount(rejectCount)
                .activeProfile(activeProfile.toUpperCase())
                .ip(Ipv4Util.LOCAL_IP)
                .keepAliveTime(executor.getKeepAliveTime(TimeUnit.SECONDS))
                .port(port)
                .currentLoad((int) Math.round((executor.getActiveCount() * 100.0) / executor.getMaximumPoolSize()) + "%")
                .peakLoad((int) Math.round((executor.getLargestPoolSize() * 100.0) / executor.getMaximumPoolSize()) + "%")
                .freeMemory(getFreeMemory())
                .memoryUsagePercentage(getMemoryProportion())
                .status(getThreadPoolState(executor))
                .currentTime(DateUtil.now())
                .build();
    }


    private String getThreadPoolState(ThreadPoolExecutor executor) {
        try {
            Method runStateLessThan = ReflectUtil.getMethodByName(ThreadPoolExecutor.class, "runStateLessThan");
            ReflectUtil.setAccessible(runStateLessThan);
            AtomicInteger ctl = (AtomicInteger) ReflectUtil.getFieldValue(executor, "ctl");
            int shutdown = (int) ReflectUtil.getFieldValue(executor, "SHUTDOWN");
            boolean runStateLessThanBool = ReflectUtil.invoke(executor, runStateLessThan, ctl.get(), shutdown);
            if (runStateLessThanBool) {
                return "Running";
            }

            Method runStateAtLeast = ReflectUtil.getMethodByName(ThreadPoolExecutor.class, "runStateAtLeast");
            ReflectUtil.setAccessible(runStateAtLeast);
            int terminated = (int) ReflectUtil.getFieldValue(executor, "TERMINATED");
            String resultStatus = ReflectUtil.invoke(executor, runStateAtLeast, ctl.get(), terminated) ? "Terminated" : "Shutting down";
            return resultStatus;
        } catch (Exception ex) {
            log.error("Failed to get thread pool status.", ex);
        }

        return "UNKNOWN";
    }
}
