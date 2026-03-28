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

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.nageoffer.onethread.dashboard.dev.server.config.OneThreadProperties;
import com.nageoffer.onethread.dashboard.dev.server.dto.UserDetailRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.dto.UserLoginReqDTO;
import com.nageoffer.onethread.dashboard.dev.server.dto.UserLoginRespDTO;
import com.nageoffer.onethread.dashboard.dev.server.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户业务接口实现层
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 * 开发时间：2025-05-17
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final OneThreadProperties oneThreadProperties;

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        String actualAuth = requestParam.getUsername() + "," + requestParam.getPassword();
        if (!oneThreadProperties.getUsers().contains(actualAuth)) {
            throw new RuntimeException("Invalid username or password");
        }

        StpUtil.login(requestParam.getUsername());
        SaSession session = StpUtil.getSession();
        session.set(
                "user",
                JSON.toJSONString(
                        UserDetailRespDTO.builder()
                                .userId("1")
                                .homePath("/")
                                .realName("程序员马丁")
                                .username(requestParam.getUsername())
                                .desc("ding.ma@apache.org")
                                .avatar("https://oss.open8gu.com/IMG_3714.JPG")
                                .build()
                )
        );

        return UserLoginRespDTO.builder()
                .id("1") // 因为用户模块非主要，这里固定写
                .realName("程序员马丁") // 因为用户模块非主要，这里固定写
                .username(requestParam.getUsername())
                .password(requestParam.getPassword()) // 前端用的 https://github.com/vbenjs/vue-vben-admin，为了兼容框架返回。正常登录不需要返回密码
                .accessToken(StpUtil.getTokenValue())
                .build();
    }

    @Override
    public UserDetailRespDTO getUser() {
        return JSON.parseObject(StpUtil.getSession().get("user").toString(), UserDetailRespDTO.class);
    }
}
