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

package com.nageoffer.onethread.dashboard.dev.server.remote.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.nageoffer.onethread.dashboard.dev.server.remote.dto.NacosConfigDetailRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.remote.dto.NacosConfigListRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.remote.dto.NacosConfigRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.remote.dto.NacosServiceListRespDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Nacos 代理客户端
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 * 开发时间：2025-05-18
 */
@Slf4j
@Component
public class NacosProxyClient {

    @Value("${onethread.nacos.server-addr}")
    private String serverAddr;

    /**
     * 查询命名空间下配置文件集合
     *
     * @param namespace 命名空间
     * @return 配置文件集合
     */
    public List<NacosConfigRespDTO> listConfig(String namespace) {
        String url = serverAddr + "/nacos/v1/cs/configs";

        HttpResponse response = HttpRequest.get(url)
                .form("pageNo", "1")
                .form("pageSize", "100") // 默认单个 namespace 最大 100 条数据，如果超过写个 while 循环读取即可
                .form("tenant", Objects.equals(namespace, "public") ? "" : namespace)
                .form("search", "blur")
                .form("dataId", "")
                .form("group", "")
                .form("appName", "")
                .form("config_tags", "")
                .form("search", "blur")
                .execute();

        String result = response.body();
        if (!response.isOk()) {
            throw new RuntimeException("Nacos server returned error.");
        }

        NacosConfigListRespDTO nacosRemoteResult = JSON.parseObject(result, NacosConfigListRespDTO.class);
        return nacosRemoteResult.getPageItems();
    }

    /**
     * 查询配置明细信息
     *
     * @param namespace 命名空间
     * @param dataId    数据 ID
     * @param group     分组标识
     * @return 配置明细
     */
    public NacosConfigDetailRespDTO getConfig(String namespace, String dataId, String group) {
        String url = serverAddr + "/nacos/v1/cs/configs";

        // 构建请求并发送
        HttpResponse response = HttpRequest.get(url)
                .form("dataId", dataId)
                .form("group", group)
                .form("namespaceId", Objects.equals(namespace, "public") ? "" : namespace)
                .form("tenant", Objects.equals(namespace, "public") ? "" : namespace)
                .form("show", "all")
                .execute();

        String result = response.body();
        if (!response.isOk()) {
            throw new RuntimeException("Nacos server returned error.");
        }

        return JSON.parseObject(result, NacosConfigDetailRespDTO.class);
    }

    /**
     * 发布配置
     *
     * @param namespace   命名空间
     * @param dataId      数据 ID
     * @param group       分组标识
     * @param content     配置文件内容
     * @param contentType 配置文件内容文件格式
     */
    public void publishConfig(String namespace, String dataId, String group, String appName, String id, String md5, String content, String contentType) {
        String url = serverAddr + "/nacos/v1/cs/configs";

        Map<String, Object> form = new HashMap<>();
        form.put("tenant", Objects.equals(namespace, "public") ? "" : namespace);
        form.put("dataId", dataId);
        form.put("group", group);
        form.put("appName", appName);
        form.put("id", id);
        form.put("md5", md5);
        form.put("content", content);
        form.put("type", contentType);

        // 发起 POST 请求
        HttpResponse response = HttpRequest.post(url)
                .form(form)
                .execute();

        if (!response.isOk()) {
            throw new RuntimeException("Nacos server returned error.");
        }
    }

    /**
     * 查询命名空间下服务明细
     *
     * @param namespace   命名空间
     * @param serviceName 服务名
     * @return 服务明细响应
     */
    public NacosServiceListRespDTO getService(String namespace, String serviceName) {
        String url = serverAddr + "/nacos/v1/ns/catalog/instances";

        HttpResponse response = HttpRequest.get(url)
                .form("pageNo", "1")
                .form("pageSize", "100") // 默认单个 service 最大 100 条数据，如果超过写个 while 循环读取即可
                .form("clusterName", "DEFAULT")
                .form("groupName", "DEFAULT_GROUP")
                .form("serviceName", serviceName)
                .form("namespaceId", Objects.equals(namespace, "public") ? "" : namespace)
                .execute();

        String result = response.body();
        if (!response.isOk()) {
            log.warn(result);
            return NacosServiceListRespDTO.builder()
                    .count(0)
                    .build();
        }

        return JSON.parseObject(result, NacosServiceListRespDTO.class);
    }
}
