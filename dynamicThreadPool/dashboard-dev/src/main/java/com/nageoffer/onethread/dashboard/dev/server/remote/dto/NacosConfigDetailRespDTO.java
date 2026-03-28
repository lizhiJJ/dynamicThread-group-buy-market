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

package com.nageoffer.onethread.dashboard.dev.server.remote.dto;

import lombok.Data;

/**
 * Nacos 配置详情响应实体
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 * 开发时间：2025-05-18
 */
@Data
public class NacosConfigDetailRespDTO {

    /**
     * 配置 ID（唯一标识）
     */
    private String id;

    /**
     * 配置的 Data ID
     */
    private String dataId;

    /**
     * 配置所属分组，例如：DEFAULT_GROUP
     */
    private String group;

    /**
     * 配置的完整内容（YAML、JSON 等）
     */
    private String content;

    /**
     * 配置内容的 MD5 值，用于校验变更
     */
    private String md5;

    /**
     * 加密数据密钥（如未启用加密则为空）
     */
    private String encryptedDataKey;

    /**
     * 租户标识（通常是命名空间）
     */
    private String tenant;

    /**
     * 应用名称，例如 onethread-framework
     */
    private String appName;

    /**
     * 配置类型（yaml、properties 等）
     */
    private String type;

    /**
     * 创建时间（时间戳，单位毫秒）
     */
    private Long createTime;

    /**
     * 修改时间（时间戳，单位毫秒）
     */
    private Long modifyTime;

    /**
     * 创建用户（若有记录）
     */
    private String createUser;

    /**
     * 创建 IP（可能是本地或远程地址）
     */
    private String createIp;

    /**
     * 配置描述（可选）
     */
    private String desc;

    /**
     * 配置用途（可选）
     */
    private String use;

    /**
     * 生效环境（可选）
     */
    private String effect;

    /**
     * 配置 schema 定义（用于 JSON/YAML 校验，通常为空）
     */
    private String schema;

    /**
     * 配置标签（用于搜索过滤，可能为 null）
     */
    private String configTags;
}
