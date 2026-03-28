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

package com.nageoffer.onethread.core.parser;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import org.yaml.snakeyaml.Yaml;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

/**
 * YAML 类型配置文件解析器
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 * 开发时间：2025-04-24
 */
public class YamlConfigParser extends AbstractConfigParser {

    private static final String INDEX_PREFIX = "[";
    private static final String INDEX_SUFFIX = "]";
    private static final String PATH_SEPARATOR = ".";

    @Override
    public Map<Object, Object> doParse(String configuration) {
        return Optional.ofNullable(configuration)
                .filter(StrUtil::isNotEmpty)
                .map(this::parseYamlDocument)
                .map(this::normalizeHierarchy)
                .orElseGet(Collections::emptyMap);
    }

    @Override
    public List<ConfigFileTypeEnum> getConfigFileTypes() {
        return List.of(ConfigFileTypeEnum.YAML, ConfigFileTypeEnum.YML);
    }

    private Map<Object, Object> parseYamlDocument(String content) {
        return Optional.ofNullable(new Yaml().load(content))
                .filter(obj -> obj instanceof Map)  // 类型安全检查
                .map(obj -> (Map<Object, Object>) obj)  // 安全类型转换
                .filter(map -> !MapUtil.isEmpty(map))
                .orElseGet(Collections::emptyMap);
    }

    private Map<Object, Object> normalizeHierarchy(Map<Object, Object> nestedData) {
        Map<Object, Object> flattenedData = new LinkedHashMap<>();
        processNestedElements(flattenedData, nestedData, null);
        return flattenedData;
    }

    private void processNestedElements(Map<Object, Object> target, Object current, String currentPath) {
        if (current instanceof Map) {
            handleMapEntries(target, (Map<?, ?>) current, currentPath);
        } else if (current instanceof Iterable) {
            handleCollectionItems(target, (Iterable<?>) current, currentPath);
        } else {
            persistLeafValue(target, currentPath, current);
        }
    }

    private void handleMapEntries(Map<Object, Object> target, Map<?, ?> entries, String parentPath) {
        entries.forEach((key, value) ->
                processNestedElements(target, value, buildPathSegment(parentPath, key))
        );
    }

    private void handleCollectionItems(Map<Object, Object> target, Iterable<?> items, String basePath) {
        List<?> elements = StreamSupport.stream(items.spliterator(), false)
                .collect(Collectors.toList());
        IntStream.range(0, elements.size())
                .forEach(index -> processNestedElements(
                        target,
                        elements.get(index),
                        createIndexedPath(basePath, index)
                ));
    }

    private String buildPathSegment(String existingPath, Object key) {
        return existingPath == null ?
                key.toString() :
                existingPath + PATH_SEPARATOR + key;
    }

    private String createIndexedPath(String basePath, int index) {
        return basePath + INDEX_PREFIX + index + INDEX_SUFFIX;
    }

    private void persistLeafValue(Map<Object, Object> target, String path, Object value) {
        if (path != null) {
            String normalizedPath = path.replace(PATH_SEPARATOR + INDEX_PREFIX, INDEX_PREFIX);
            target.put(normalizedPath, value != null ? value.toString() : null);
        }
    }
}
