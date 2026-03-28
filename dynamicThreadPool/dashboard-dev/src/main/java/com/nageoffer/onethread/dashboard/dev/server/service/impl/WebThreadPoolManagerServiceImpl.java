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

package com.nageoffer.onethread.dashboard.dev.server.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.nageoffer.onethread.dashboard.dev.server.common.Result;
import com.nageoffer.onethread.dashboard.dev.server.config.DashBoardConfigProperties;
import com.nageoffer.onethread.dashboard.dev.server.config.OneThreadProperties;
import com.nageoffer.onethread.dashboard.dev.server.dto.WebThreadPoolDetailRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.dto.WebThreadPoolListReqDTO;
import com.nageoffer.onethread.dashboard.dev.server.dto.WebThreadPoolStateRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.dto.WebThreadPoolUpdateReqDTO;
import com.nageoffer.onethread.dashboard.dev.server.remote.client.NacosProxyClient;
import com.nageoffer.onethread.dashboard.dev.server.remote.dto.NacosConfigDetailRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.remote.dto.NacosConfigRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.remote.dto.NacosServiceListRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.remote.dto.NacosServiceRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.service.WebThreadPoolManagerService;
import com.nageoffer.onethread.dashboard.dev.server.service.handler.YamlConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Web 线程池管理接口实现层
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 * 开发时间：2025-05-23
 */
@Service
@RequiredArgsConstructor
public class WebThreadPoolManagerServiceImpl implements WebThreadPoolManagerService {

    private final OneThreadProperties oneThreadProperties;
    private final NacosProxyClient nacosProxyClient;
    private final YamlConfigParser yamlConfigParser;

    @Override
    public List<WebThreadPoolDetailRespDTO> listThreadPool(WebThreadPoolListReqDTO requestParam) {
        List<WebThreadPoolDetailRespDTO> threadPools = new ArrayList<>();

        List<String> namespaces = new ArrayList<>(oneThreadProperties.getNamespaces());
        String requestedNamespace = requestParam.getNamespace();
        String requestedServiceName = requestParam.getServiceName();
        if (StrUtil.isNotBlank(requestedNamespace) && namespaces.contains(requestedNamespace)) {
            // 只保留匹配的 namespace
            namespaces.clear();
            namespaces.add(requestedNamespace);
        }

        namespaces.forEach(namespace -> {
            List<NacosConfigRespDTO> nacosConfigResponse = nacosProxyClient.listConfig(namespace);
            if (CollUtil.isNotEmpty(nacosConfigResponse)) {
                nacosConfigResponse
                        .stream()
                        .filter(each -> {
                            if (StrUtil.isBlank(each.getAppName())) {
                                return false;
                            }
                            return StrUtil.isBlank(requestedServiceName) || Objects.equals(each.getAppName(), requestedServiceName);
                        })
                        .forEach(config -> {
                            // 此处应根据配置文件的类型进行判断，比如 YAML 或者 Properties，为了简化非核心流程，默认处理 YAML
                            Map<Object, Object> configInfoMap = yamlConfigParser.doParse(config.getContent());

                            // 将 Map 值绑定到 DashBoardConfigProperties 类属性
                            ConfigurationPropertySource sources = new MapConfigurationPropertySource(configInfoMap);
                            Binder binder = new Binder(sources);

                            DashBoardConfigProperties refresherProperties;
                            try {
                                refresherProperties = binder
                                        .bind("onethread", Bindable.of(DashBoardConfigProperties.class))
                                        .orElseThrow(() -> new IllegalArgumentException("onethread config binding failed"));
                            } catch (Exception e) {
                                return;
                            }

                            NacosServiceListRespDTO service = nacosProxyClient.getService(namespace, config.getAppName());
                            DashBoardConfigProperties.WebThreadPoolExecutorConfig webThreadPoolConfig = refresherProperties.getWeb();
                            if (service == null || CollUtil.isEmpty(service.getServiceList()) || webThreadPoolConfig == null) {
                                return;
                            }

                            NacosServiceRespDTO nacosService = service.getServiceList().get(0);
                            String networkAddress = nacosService.getIp() + ":" + nacosService.getPort();

                            Result<WebThreadPoolStateRespDTO> result;
                            try {
                                String resultStr = HttpUtil.get("http://" + networkAddress + "/web/thread-pool", 1000);
                                result = JSON.parseObject(resultStr, new TypeReference<>() {
                                });
                            } catch (Exception e) {
                                return;
                            }
                            String webContainerName = result.getData().getWebContainerName();

                            WebThreadPoolDetailRespDTO webThreadPool = WebThreadPoolDetailRespDTO.builder()
                                    .webContainerName(webContainerName)
                                    .namespace(namespace)
                                    .serviceName(config.getAppName())
                                    .dataId(config.getDataId())
                                    .group(config.getGroup())
                                    .instanceCount(service.getCount())
                                    .corePoolSize(webThreadPoolConfig.getCorePoolSize())
                                    .maximumPoolSize(webThreadPoolConfig.getMaximumPoolSize())
                                    .keepAliveTime(webThreadPoolConfig.getKeepAliveTime())
                                    .notify(BeanUtil.toBean(webThreadPoolConfig.getNotify(), WebThreadPoolDetailRespDTO.NotifyConfig.class))
                                    .build();
                            threadPools.add(webThreadPool);
                        });
            }
        });

        return threadPools;
    }

    @SneakyThrows
    @Override
    public void updateGlobalThreadPool(WebThreadPoolUpdateReqDTO requestParam) {
        NacosConfigDetailRespDTO configDetail = nacosProxyClient.getConfig(requestParam.getNamespace(), requestParam.getDataId(), requestParam.getGroup());
        String originalContent = configDetail.getContent();

        Map<Object, Object> configInfoMap = yamlConfigParser.doParse(originalContent);
        ConfigurationPropertySource source = new MapConfigurationPropertySource(configInfoMap);

        Binder binder = new Binder(source);
        DashBoardConfigProperties onethread = binder.bind("onethread", Bindable.of(DashBoardConfigProperties.class))
                .orElseThrow(() -> new RuntimeException("binding failed"));

        onethread.setWeb(BeanUtil.toBean(requestParam, DashBoardConfigProperties.WebThreadPoolExecutorConfig.class));

        Map<String, Object> updatedMap = new LinkedHashMap<>();
        updatedMap.put("onethread", onethread);

        YAMLFactory factory = new YAMLFactory();
        factory.disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER); // 去除 Yaml 字符串开头 ---
        factory.enable(YAMLGenerator.Feature.MINIMIZE_QUOTES);

        ObjectMapper objectMapper = new ObjectMapper(factory);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL); // 去除 null 字段
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.KEBAB_CASE); // 驼峰命名转 -，比如 corePooSize 转 core-pool-size

        String yamlStr = objectMapper.writeValueAsString(Collections.singletonMap("onethread", onethread));
        nacosProxyClient.publishConfig(requestParam.getNamespace(), requestParam.getDataId(), requestParam.getGroup(), configDetail.getAppName(), configDetail.getId(), configDetail.getMd5(), yamlStr, "yaml");
    }
}
