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

package com.nageoffer.onethread.core.executor;

import com.nageoffer.onethread.core.executor.support.RejectedProxyInvocationHandler;
import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Proxy;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增强的动态、报警和受监控的线程池 oneThread
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 * 开发时间：2025-04-20
 */
@Slf4j
public class OneThreadExecutor extends ThreadPoolExecutor {

    /**
     * 线程池唯一标识，用来动态变更参数等
     */
    @Getter
    private final String threadPoolId;

    /**
     * 线程池拒绝策略执行次数
     */
    @Getter
    private final AtomicLong rejectCount = new AtomicLong();

    /**
     * 等待终止时间，单位毫秒
     */
    private long awaitTerminationMillis;

    /**
     * Creates a new {@code ExtensibleThreadPoolExecutor} with the given initial parameters.
     *
     * @param threadPoolId           thread-pool id
     * @param corePoolSize           the number of threads to keep in the pool, even
     *                               if they are idle, unless {@code allowCoreThreadTimeOut} is set
     * @param maximumPoolSize        the maximum number of threads to allow in the
     *                               pool
     * @param keepAliveTime          when the number of threads is greater than
     *                               the core, this is the maximum time that excess idle threads
     *                               will wait for new tasks before terminating.
     * @param unit                   the time unit for the {@code keepAliveTime} argument
     * @param workQueue              the queue to use for holding tasks before they are
     *                               executed.  This queue will hold only the {@code Runnable}
     *                               tasks submitted by the {@code execute} method.
     * @param threadFactory          the factory to use when the executor
     *                               creates a new thread
     * @param handler                the handler to use when execution is blocked
     *                               because the thread bounds and queue capacities are reached
     * @param awaitTerminationMillis the maximum time to wait
     * @throws IllegalArgumentException if one of the following holds:<br>
     *                                  {@code corePoolSize < 0}<br>
     *                                  {@code keepAliveTime < 0}<br>
     *                                  {@code maximumPoolSize <= 0}<br>
     *                                  {@code maximumPoolSize < corePoolSize}
     * @throws NullPointerException     if {@code workQueue} or {@code unit}
     *                                  or {@code threadFactory} or {@code handler} is null
     */
    public OneThreadExecutor(
            @NonNull String threadPoolId,
            int corePoolSize,
            int maximumPoolSize,
            long keepAliveTime,
            @NonNull TimeUnit unit,
            @NonNull BlockingQueue<Runnable> workQueue,
            @NonNull ThreadFactory threadFactory,
            @NonNull RejectedExecutionHandler handler,
            long awaitTerminationMillis) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);

        // 通过动态代理设置拒绝策略执行次数
        setRejectedExecutionHandler(handler);

        // 设置动态线程池扩展属性：线程池 ID 标识
        this.threadPoolId = threadPoolId;

        // 设置等待终止时间，单位毫秒
        this.awaitTerminationMillis = awaitTerminationMillis;
    }

    /**
     * 当前采用轻量级的 Lambda 静态代理方式实现增强，同时也支持使用基于 JDK 动态代理机制的拒绝策略替换方案
     * <pre>
     * RejectedExecutionHandler rejectedProxy = (RejectedExecutionHandler) Proxy
     *         .newProxyInstance(
     *                 handler.getClass().getClassLoader(),
     *                 new Class[]{RejectedExecutionHandler.class},
     *                 new RejectedProxyInvocationHandler(handler, rejectCount)
     *         );
     * </pre>
     */
    @Override
    public void setRejectedExecutionHandler(RejectedExecutionHandler handler) {
        RejectedExecutionHandler handlerWrapper = new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                rejectCount.incrementAndGet();
                handler.rejectedExecution(r, executor);
            }

            @Override
            public String toString() {
                return handler.getClass().getSimpleName();
            }
        };

        super.setRejectedExecutionHandler(handlerWrapper);
    }

    @Override
    public void shutdown() {
        if (isShutdown()) {
            return;
        }

        super.shutdown();
        if (this.awaitTerminationMillis <= 0) {
            return;
        }

        log.info("Before shutting down ExecutorService {}", threadPoolId);
        try {
            boolean isTerminated = this.awaitTermination(this.awaitTerminationMillis, TimeUnit.MILLISECONDS);
            if (!isTerminated) {
                log.warn("Timed out while waiting for executor {} to terminate.", threadPoolId);
            } else {
                log.info("ExecutorService {} has been shutdown.", threadPoolId);
            }
        } catch (InterruptedException ex) {
            log.warn("Interrupted while waiting for executor {} to terminate.", threadPoolId);
            Thread.currentThread().interrupt();
        }
    }
}
