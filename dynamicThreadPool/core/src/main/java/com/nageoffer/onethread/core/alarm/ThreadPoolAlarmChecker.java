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

package com.nageoffer.onethread.core.alarm;

import cn.hutool.core.date.DateUtil;
import com.nageoffer.onethread.core.config.ApplicationProperties;
import com.nageoffer.onethread.core.executor.OneThreadExecutor;
import com.nageoffer.onethread.core.executor.OneThreadRegistry;
import com.nageoffer.onethread.core.executor.ThreadPoolExecutorHolder;
import com.nageoffer.onethread.core.executor.ThreadPoolExecutorProperties;
import com.nageoffer.onethread.core.notification.dto.ThreadPoolAlarmNotifyDTO;
import com.nageoffer.onethread.core.notification.service.NotifierDispatcher;
import com.nageoffer.onethread.core.toolkit.ThreadFactoryBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 线程池运行状态报警检查器
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 * 开发时间：2025-05-04
 */
@Slf4j
@RequiredArgsConstructor
public class ThreadPoolAlarmChecker {

    private final NotifierDispatcher notifierDispatcher;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
            1,
            ThreadFactoryBuilder.builder()
                    .namePrefix("scheduler_thread-pool_alarm_checker")
                    .build()
    );
    private final Map<String, Long> lastRejectCountMap = new ConcurrentHashMap<>();

    /**
     * 启动定时检查任务
     */
    public void start() {
        // 每10秒检查一次，初始延迟0秒
        scheduler.scheduleWithFixedDelay(this::checkAlarm, 0, 5, TimeUnit.SECONDS);
    }

    /**
     * 停止报警检查
     */
    public void stop() {
        if (!scheduler.isShutdown()) {
            scheduler.shutdown();
        }
    }

    /**
     * 报警检查核心逻辑
     */
    private void checkAlarm() {
        Collection<ThreadPoolExecutorHolder> holders = OneThreadRegistry.getAllHolders();
        for (ThreadPoolExecutorHolder holder : holders) {
            if (holder.getExecutorProperties().getAlarm().getEnable()) {
                checkQueueUsage(holder);
                checkActiveRate(holder);
                checkRejectCount(holder);
            }
        }
    }

    /**
     * 检查队列使用率
     */
    private void checkQueueUsage(ThreadPoolExecutorHolder holder) {
        ThreadPoolExecutor executor = holder.getExecutor();
        ThreadPoolExecutorProperties properties = holder.getExecutorProperties();

        BlockingQueue<?> queue = executor.getQueue();
        int queueSize = queue.size();
        int capacity = queueSize + queue.remainingCapacity();

        if (capacity == 0) {
            return;
        }

        int usageRate = (int) Math.round((queueSize * 100.0) / capacity);
        int threshold = properties.getAlarm().getQueueThreshold();

        if (usageRate >= threshold) {
            sendAlarmMessage("Capacity", holder);
        }
    }

    /**
     * 检查线程活跃度（活跃线程数 / 最大线程数）
     */
    private void checkActiveRate(ThreadPoolExecutorHolder holder) {
        ThreadPoolExecutor executor = holder.getExecutor();
        ThreadPoolExecutorProperties properties = holder.getExecutorProperties();

        int activeCount = executor.getActiveCount(); // API 有锁，避免高频率调用
        int maximumPoolSize = executor.getMaximumPoolSize();

        if (maximumPoolSize == 0) {
            return;
        }

        int activeRate = (int) Math.round((activeCount * 100.0) / maximumPoolSize);
        int threshold = properties.getAlarm().getActiveThreshold();

        if (activeRate >= threshold) {
            sendAlarmMessage("Activity", holder);
        }
    }

    /**
     * 检查拒绝策略执行次数
     */
    private void checkRejectCount(ThreadPoolExecutorHolder holder) {
        ThreadPoolExecutor executor = holder.getExecutor();
        String threadPoolId = holder.getThreadPoolId();

        // 只处理自定义线程池类型
        if (!(executor instanceof OneThreadExecutor)) {
            return;
        }

        OneThreadExecutor oneThreadExecutor = (OneThreadExecutor) executor;
        long currentRejectCount = oneThreadExecutor.getRejectCount().get();
        long lastRejectCount = lastRejectCountMap.getOrDefault(threadPoolId, 0L);

        // 首次初始化或拒绝次数增加时触发
        if (currentRejectCount > lastRejectCount) {
            sendAlarmMessage("Reject", holder);
            // 更新最后记录值
            lastRejectCountMap.put(threadPoolId, currentRejectCount);
        }
    }

    private void sendAlarmMessage(String alarmType, ThreadPoolExecutorHolder holder) {
        ThreadPoolExecutorProperties properties = holder.getExecutorProperties();
        String threadPoolId = holder.getThreadPoolId();

        ThreadPoolAlarmNotifyDTO alarm = ThreadPoolAlarmNotifyDTO.builder()
                .alarmType(alarmType)
                .threadPoolId(threadPoolId)
                .interval(properties.getNotify().getInterval())
                .build();

        alarm.setSupplier(() -> {
            try {
                alarm.setIdentify(InetAddress.getLocalHost().getHostAddress());
            } catch (UnknownHostException e) {
                log.warn("Error in obtaining HostAddress", e);
            }

            ThreadPoolExecutor executor = holder.getExecutor();
            BlockingQueue<?> queue = executor.getQueue();

            int size = queue.size();
            int remaining = queue.remainingCapacity();
            long rejectCount = (executor instanceof OneThreadExecutor)
                    ? ((OneThreadExecutor) executor).getRejectCount().get()
                    : -1L;

            alarm.setCorePoolSize(executor.getCorePoolSize())
                    .setMaximumPoolSize(executor.getMaximumPoolSize())
                    .setActivePoolSize(executor.getActiveCount())  // API 有锁，避免高频率调用
                    .setCurrentPoolSize(executor.getPoolSize()) // API 有锁，避免高频率调用
                    .setCompletedTaskCount(executor.getCompletedTaskCount()) // API 有锁，避免高频率调用
                    .setLargestPoolSize(executor.getLargestPoolSize()) // API 有锁，避免高频率调用
                    .setWorkQueueName(queue.getClass().getSimpleName())
                    .setWorkQueueSize(size)
                    .setWorkQueueRemainingCapacity(remaining)
                    .setWorkQueueCapacity(size + remaining)
                    .setRejectedHandlerName(executor.getRejectedExecutionHandler().toString())
                    .setRejectCount(rejectCount)
                    .setCurrentTime(DateUtil.now())
                    .setApplicationName(ApplicationProperties.getApplicationName())
                    .setActiveProfile(ApplicationProperties.getActiveProfile())
                    .setReceives(properties.getNotify().getReceives());
            return alarm;
        });

        notifierDispatcher.sendAlarmMessage(alarm);
    }
}
