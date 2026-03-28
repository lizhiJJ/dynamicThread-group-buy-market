# 拼团营销系统 (Group-Buy Market) 全能面试解析指南

这份指南将从宏观架构到微观代码实现，从流转细节到中间件应用，全方位无死角地为你拆解 `group-buy-market` 项目。你可以直接将这些内容转化为你的面试话术。

---

## 一、 项目全景与应用架构分析

### 1. 业务背景与系统定位
本项目是一个为电商平台或独立站提供支持的**高并发拼团营销系统**。在秒杀、拼团等大促场景下，系统面临着瞬时高并发读写、库存超防卖、长事务以及分布式数据一致性等诸多挑战。本系统通过精巧的架构和中间件组合，稳定支撑了相关业务线。

### 2. DDD (领域驱动设计) 分层架构
系统摒弃了传统的 MVC 架构（导致大泥球代码），采用了标准的 **DDD 四层架构**，这也是面试中展示架构思维的亮点：

*   **`group-buy-market-api` (API 契约层)**：定义了系统对外的交互契约（全都是 `IDCCService`, `IMarketIndexService` 等 `interface` 以及 DTO 对象）。它使得外部调用方（如网关、其他微服务）只需引入此包即可调用，实现解耦。
*   **`group-buy-market-app` (应用启动层)**：包含 Spring Boot 的启动类 `Application` 和一系列全局组件配置，如 `RabbitMQConfig`、`RedisClientConfig`、`ThreadPoolConfig`。
*   **`group-buy-market-trigger` (触发源层/接入层)**：
    *   **HTTP 接口**: 如 `MarketTradeController`，处理外部 HTTP 请求。
    *   **定时任务 (Job)**: 如 `TimeoutRefundJob`、`GroupBuyNotifyJob`，处理延时与周期性任务。
    *   **消息监听 (Listener)**: 如 `RefundSuccessTopicListener`，由外部 MQ 消息触发业务。
*   **`group-buy-market-domain` (核心领域层)**：系统的**心脏**。所有核心业务逻辑都在这里。分为三个主要子域：
    *   **`activity` (营销活动域)**: 负责拼团规则、试算、优惠策略、人群标签过滤。
    *   **`trade` (交易域)**: 负责锁单、结算、退单的整个生命周期状态流转。
    *   **`tag` (人群标签域)**: 负责用户身份的标识与圈选。
*   **`group-buy-market-infrastructure` (基础设施层)**：
    *   为领域层提供底层支撑（依赖倒置）。包含了 MyBatis DAOs（如 `IGroupBuyOrderDao`）、Redis 交互层、消息发送层。它实现了 Domain 层定义的 `IRepository` 接口。
*   **`group-buy-market-types` (基础数据与公共类型)**：存放公共的枚举 (`GroupBuyOrderEnumVO`)、常量 (`Constants`)、全局异常 (`AppException`) 和标准响应实体 (`Response`)。

> **面试话术：** “我的项目采用了标准的 DDD 架构，最大的好处是实现了**业务复杂度和技术复杂度的分离**。我的 Domain 层不依赖任何具体的数据库框架或 MQ 框架，只依赖抽象。这样即使未来从 RabbitMQ 换成 RocketMQ，或者从 MySQL 换成 TiDB，我核心的拼团规则和防超卖逻辑一行代码都不用改。”

### 3. 核心技术栈与组件运用
*   **Spring Boot 2.x**: 简化了 Spring 应用初始搭建以及开发过程。
*   **MySQL & MyBatis & Druid**: 作为主要的关系型数据存储。
*   **Redis & Redisson**: 扮演着极其重要的角色（核心考点）。用于缓存配置、**利用 `incr` 防超卖库存扣减**、**Redisson 分布式锁**（用于定时任务防重夺取、恢复库存防并发）。
*   **RabbitMQ**: 用于解耦**成团后的发奖/通知**以及**退单后的库存恢复**。保障最终一致性。
*   **Guava Cache**: 本地缓存（可选优化点），降低对 Redis 的网络 IO 压力。
*   **Wrench (自研/开源工具包)**: 封装了通用的设计模式框架（`StrategyHandler` 策略树、`BusinessLinkedList` 责任链编排），极大提升了代码的优雅度和可维护性。
*   **DCC (Dynamic Config Center)**: 借助 Redis Pub/Sub（发布订阅机制）打造的轻量级动态配置中心，用于随时做**活动降级**和**限流切量**。

---

## 二、 核心业务流程与代码细节全量梳理

### 场景 1：拼团首页展示与营销试算 (Query & Trial Calculation)
**业务痛点**：大促时，用户疯狂刷新拼团页面，这涉及到活动状态、库存、人群标签、当前组队进度、各种折扣算价的复合查询。如果完全串行查询 DB，接口 RT (响应时间) 会爆炸，打满 Tomcat 线程池。

**解决流程 (`MarketIndexController#queryGroupBuyMarketConfig`)**：
1.  **DCC 动态拦截控制 (`SwitchNode`)**：基于 Redis 订阅，如果发现系统负载过高，直接触发 `SwitchNode` 的 `downgradeSwitch` (降级开关) 或 `cutRange` (切量比例)，使得大部分请求直接快速失败，保护后端。
2.  **Guava RateLimiter 限流**：控制器上挂载了拦截器 `@RateLimiterAccessInterceptor`，底层通常基于令牌桶算法，在单机维度进行流量管制。
3.  **多线程并行加载与算价 (`MarketNode`)**：利用 `ThreadPoolExecutor` 线程池和 `FutureTask`并发拉取数据。
    *   线程 A：查 DB/Redis 拿 `GroupBuyActivityDiscountVO` (拼团活动配置)。
    *   线程 B：查 DB/Redis 拿 `SkuVO` (商品原始数据)。
    *   主线程执行 `futureTask.get(timeout, TimeUnit.MILLISECONDS)` 进行最多 5 秒的超时等待。
    *   *技术点：将多次串行 RPC/网络 IO 转为并行，极大降低接口耗时。若任何一步超时，走 `ErrorNode` 统一返回异常提示。*
4.  **策略模式算价 (`IDiscountCalculateService`)**：根据拉取到的优惠类型，通过 Spring 的 `Map<String, IDiscountCalculateService>` 动态注入路由到特定的实现类（如 `ZKCalculateService` 算折扣，内部使用 `BigDecimal` 防止精度丢失）。
5.  **人群标签节点 (`TagNode`)**：如果活动配置了特定人群（TagId），校验用户是否在名单内。

### 场景 2：基于 Redis 的高并发交易锁单 (Lock Market Pay Order)
**业务痛点**：如何保证一堆用户同时抢几百个拼团名额不发生“超卖”？直接查 DB `where target_count > lock_count` 必定在并发下出问题；或者用行级互斥悲观锁 `FOR UPDATE` 会导致数据库严重阻塞死锁。

**解决流程 (`MarketTradeController#lockMarketPayOrder` -> `TradeLockOrderService`)**：
这里使用了自研框架封装的**责任链模式 (`TradeLockRuleFilterFactory`)**，依次通过：
1.  **`ActivityUsabilityRuleFilter` (时效拦截)**：校验活动是否在有效期内、状态是否 `EFFECTIVE`。
2.  **`UserTakeLimitRuleFilter` (用户限购)**：校验该用户单次活动参与次数是否超越配置上限。
3.  **核心：`TeamStockOccupyRuleFilter` (Redis 预扣减防超卖)**：
    *   代码：`long occupy = redisService.incr(teamStockKey) + 1;`。利用 Redis 单线程执行命令的特性，原子的拿到当前是第几个锁单人。
    *   比较：`occupy > target + recoveryCount`。对比目标成团人数加上目前因为失败而恢复出来的库存数量。
    *   超卖处理：如果超出，立刻执行 `redisService.decr(teamStockKey)` 减回去，然后抛出异常 `ResponseCode.E0008`。
    *   兜底防死：在 `incr` 成功后，立刻追加一个 `setNx(lockKey, validTime + 60, TimeUnit.MINUTES)`，保留一段时间现场，防止脏数据。
4.  **最终数据落库**：经过缓存校验无误后，再落入 DB 的 `group_buy_order_list` (订单明细) 和 `group_buy_order` (团队表)。此时 DB 即使有一定延迟也不会超卖，因为入口已经被 Redis 严格把控。（此时状态为 `CREATE` 待支付）。

### 场景 3：成团结算与本地消息表应用 (Settlement)
**业务痛点**：用户支付成功（由外部支付网关回调）后，如何更新当前团的进度？并且一旦发现当前人是“最后一座”，如何保证**一定**通知到外部系统发奖发货？只用 MQ 如果此时网络断开消息丢了怎么办？

**解决流程 (`TradeSettlementOrderService`)**：
1.  **执行结算**：利用 DB 原生的排它锁，执行更新命令 `updateStatus2COMPLETE` 和 `lock_count + 1`、`complete_count + 1`。
2.  **判断成团条件**：如果发现 `TargetCount - CompleteCount == 1` (代表加上当前这单刚好凑齐)，触发成团动作。将整个队伍（`group_buy_order`）状态更变为 `COMPLETE`。
3.  **最高频面试点：本地消息表方案 (`NotifyTask`)保证最终一致性**：
    *   在这个更新订单状态的 **同一个 DB 事务中**，我会在 `notify_task` 表 `INSERT` 一条记录（包含 teamId, mq_topic, 状态=0未发送 等信息）。因为在同一个方法上加上了 `@Transactional`，两者**同生共死**。
    *   随后，`ITradeTaskService` 被异步线程唤醒，去读取这张表，将消息序列化后丢向 RabbitMQ (`topic_team_success` 交换机)。
    *   如果发送 MQ 成功，回调将 `notify_task` 状态改为成功。
    *   *兜底安全网*：即便这瞬间服务器宕机异步线程死了，还有一个定时任务 `GroupBuyNotifyJob` 每隔一段时间扫描 `notify_task` 里所有未成功的记录去重发。

### 场景 4：逆向退单与库存恢复流转 (Refund Order & Timeout Job)
**业务痛点**：用户不付款占着茅坑怎么办？用户付款了但团没满想退款，此时复杂的状态机和库存如何回退？

**解决流程 (`TradeRefundOrderService` & `TimeoutRefundJob`)**：
1.  **超时扫描清理死单**：`TimeoutRefundJob` 运用 Spring 的 `@Scheduled` 注解每 x 分钟扫描。为了防止多节点部署时都去扫，引入了 **Redisson 分布式锁** `lock.tryLock(3, 60, TimeUnit.SECONDS)`。抢到锁的机器负责干活（找出超时的 `UserGroupBuyOrderDetailEntity` 发起退单）。
2.  **退款规则责任链** (`TradeRefundRuleFilterFactory`)：校验退单是不是二次重试，数据来源对不对（防止被刷）。
3.  **处理复杂退款状态的救星：退款策略模式 (`AbstractRefundOrderStrategy`)**：
    系统目前订单状态复杂：还没支付的、支付了未成团的、成团了突然退货的。如果大段 `if-else` 一定维护不了。系统基于目前状态动态获取 `RefundTypeEnumVO` 枚举路由策略：
    *   `Unpaid2RefundStrategy` (针对超时未支付)：DB 数据状态直接置反，记录 `NotifyTask` 准备恢复 Redis 库存。
    *   `Paid2RefundStrategy` (针对已支付未成团)：DB 的 `completeCount` 也要对应 -1。
    *   `PaidTeam2RefundStrategy` (针对已成团解散)：队伍状态更新为 `COMPLETE_FAIL`。
4.  **消费 MQ 恢复 Redis 可用库存 (`RefundSuccessTopicListener`)**：
    *   MQ 消费者监听到刚刚退单成功的消息 `TeamRefundSuccess`。
    *   需要给 Redis 中的 `recoveryTeamStockKey` 执行 `incr` 做补偿累加操作（代表多出一个名额可以给别人重新抢）。
    *   **防重防死循环设计**：万一 MQ 发生不可避免的重复投递（网络抖动），会不会导致库存被加了两次？
        这里有一个精妙设计 (见 `TradeRepository#refund2AddRecovery`)：执行 `incr` 前，强制要求拿到一个基于 `orderId` 的 Redis 分布式锁 `setNx(lockKey...)`。一旦这个锁被写过，代表这个订单已经回退过库存了，直接 return 跳过。

---

## 三、 面试拓展：关键架构选型与语法/中间件原理答疑

### 1. 语法 & Java 核心类问题
*   **你刚提到用了 `ThreadPoolExecutor` 和 `FutureTask` 加快接口 RT。请详细讲一下线程池的核心参数，你在项目中是怎么配的，为什么？并且 `FutureTask` 是怎么拿到结果的？**
    *   **回答指导：** “我的线程池配置在 `ThreadPoolConfig` 中，它被声明为 Spring Bean。核心参数有 7 个：`corePoolSize`（核心常驻线程，应对日常 QPS 配置比如 5）、`maxPoolSize`（洪峰承载量，比如 20）、`keepAliveTime`、`TimeUnit`、**阻塞队列 `workQueue`**（这是防雪崩的关键，我使用了有界队列 `LinkedBlockingQueue(100)`，不能用无界队列否则可能内存溢出 OOM）、`ThreadFactory` 和 **拒绝策略 `handler`**（当队列满且最大线程数满时，我配置的是 `AbortPolicy` 抛出异常，或者根据系统容忍情况选 `CallerRunsPolicy` 让主线程去跑借而限流）。
    获取结果方面，将实现 `Callable` 的任务交给线程池执行后返回 `FutureTask`，主线程调用其 `get(timeout, TimeUnit)` 方法。内部它调用了 `LockSupport.park` 进行休眠，直到异步线程执行完并通过 `LockSupport.unpark` 唤醒它拿到结果。”
*   **代码里到处看到了 `BigDecimal`，能说说它和 Double 算钱有什么区别？你在配置舍入规则时用了什么？**
    *   **回答指导：** “拼团营销试算有算折扣券等逻辑（在 `ZKCalculateService` 中），绝对不能用 Double/Float（这会丢失精度，例如 0.1+0.2 在底层 IEEE 754 无法精确保存，得到 0.300000004）。必须传 String 构造 `BigDecimal`。计算金额时遇到除不尽或者如 99*0.8=79.2 时，我使用了 `setScale(0, RoundingMode.DOWN)` 向下取整，并手动检查 `deductionPrice.compareTo(BigDecimal.ZERO) <= 0` 保证最低支付 1 分钱 `new BigDecimal("0.01")`。”

### 2. Redis & 并发控制问题
*   **你能给我详细复盘一下你们的秒杀防超卖利用 Redis是怎么做的吗？如果是你说的 `incr`，他有没有可能引起少卖？**
    *   **回答指导：** “防超卖的核心在 `TeamStockOccupyRuleFilter` 这个责任链节点。我们使用 Redis 单线程单指令操作的高速特性，对 `group_buy_market_team_stock_key_xxx` 执行 `incr`。取得的值一旦大过 `target` (目标上限) + `recoveryCount` (补偿容量) 就会抛错。
    确实可能有**少卖**。比如用户占到了名额 (incr拿到了有效序号)，由于不可抗力报错，导致 DB 没落下单数据。或者用户干脆就不付钱！这就需要我说的 **超时未支付 Job 扫描退单** + **本地消息表发 MQ 补偿** 来把那个荒废的 `incr` 用加回到 `recoveryKey`（也就是补偿容量机制）里去中和掉，实现最终一致。我们这套组合拳同时解决了超卖和少卖。”
*   **为什么在 `TimeoutRefundJob` 里，还要用 Redisson 给定时任务加锁？**
    *   **回答指导：** “在互联网生产环境中应用都是在 K8s Pod 里集群多机多实例部署的。如果不加锁，凌晨 12 钟到了，三个微服务节点同时检测到了 10 个超时订单，就会同时发起 3 次对这 10 个订单的退单指令，这会导致 DB 极大的性能浪费和潜在的脏数据并发冲突。
    所以我们要保证这个 Job 同一时刻只能被触发一次。我们采用了 **Redisson 分布式锁** 的 `tryLock(等待时间, 租期时间, TimeUnit)`。第一个抢到的节点往下执行，其他的拿不到锁就立刻 return 后退。同时利用 Redisson 的租期（续期看门狗机制）保证了，万一执行节点因为 OOM 突然掉电宕机，锁也能在持有时间（如 60s）后自动释放，不会由于节点死锁卡住后续的一切调度流程。”

### 3. MQ 消息队列 & 本地消息表问题
*   **可以再深度讲解一下你说的‘本地消息表’机制吗？为什么要它，如果用 RabbitMQ 原生的Confirm机制/发送者确认不行吗？**
    *   **回答指导：** “MQ的发送者 Confirm 机制能在消息代理持久化后给发送方一个 ACK 告诉它已收到，但不解决这样一个核心矛盾：**先执行 DB 还是 先发 MQ？**
    如果先发 MQ 再更 DB，MQ 刚发出去 DB 断电了回滚了（或者被后面的逻辑触发回滚了）。此时外部得到通知已经发了货了，发生了**重大生产事故**。
    如果先更 DB 成功后，代码走到最下面再去向 RabbitMQ 发。万一这时候发 MQ 方法超时抛异常了呢？DB 是提交了，但是外部这辈子也收不到发货或者退款通知了（这也叫丢消息）。
    **解决方案本地消息表（经典架构手段）：** 巧妙利用关系型数据库的事务一致性（`@Transactional`）。把更新业务的 SQL 语句和 `insert into notify_task` 插入消息数据的 SQL 语句放在**同一个事务中**！如果一切顺利两者皆成功；如果出错，利用 Spring 的基于 AOP 动态代理的 `@Transactional` 事务同时让二者回滚。随后利用一个不受该事务控制的异步线程或者独立的 Job，去扫描这张 `notify_task` 表并且不断的向 MQ 重发，发成功就标记为完成。它保证了消息的 100% 不丢失与最终一致送达。”
*   **你提到通过 MQ 去恢复库存在 `RefundSuccessTopicListener` 消费端。你怎么保证 MQ 不重复消费？**
    *   **回答指导：** “MQ 在发生网络闪断（没有收到 ACK 回执给 Server）时，有补偿重试特性，会发生一条消息发两次。这就是我们必须要保证消费者接口是**幂等（Idempotent）**的原因。
    在项目的 `TradeRepository#refund2AddRecovery` 方法中，我不是简单的接到 MQ 通知就 `incr`。而是用消息里带过来的唯一业务凭证 `orderId` 拼装成一把临时分布式锁的 Key `"refund_lock_" + orderId` 执行 `setNx` 操作。如果返回 `false` 证明这笔订单的退款消息我之前已经消费并且处理完毕了，直接吞掉消息并忽略；返回 `true` 才去执行实质的库存恢复累加。”

### 4. 架构思维与设计模式总结问题
*   **为什么要用领域驱动设计 (DDD)，在这个拼团项目里体现了多少价值？相比 MVC 有何优劣？**
    *   **回答指导：** “传统的 MVC（Controller-Service-Dao）当业务发展庞大后往往变成“面条式”过程代码，Service 里面混合着调用 Redis、封装 MQ 报文等一切技术细节。
    在项目中，DDD 帮助我梳理了 `activity` 和 `trade` 这些真正的业务边界，将对象变成了充血模型。最大的价值体现在 `Infrastructure` 基础设施层的**依赖倒置（DIP 倒置原则）**。在代码中，我的 `trade` 在做退单处理时，他调用的只是 Domain 层中定义的 `ITradeRepository` 门面接口。具体的 Mybatis 和 连表 SQL 是被实现在 infra 层的 `TradeRepository` 中。这意味着明天公司禁用 MyBatis 改为 JPA，禁用 Redis 改为 Memcached，禁用 RabbitMQ 改为 RocketMQ。**我的核心拼团、锁单规则代码竟然一行都不需要删改重构**。因为领域层是“纯洁的”。它的劣势在于前期建设繁琐，有大量 DTO、Entity、VO 的互相转换导致包体积大模型厚重，并不适合简单的 CRUD 玩具项目，但在这个极具挑战的大型拼购系统中是非常值得也是必要的前期规约架构投入。”
