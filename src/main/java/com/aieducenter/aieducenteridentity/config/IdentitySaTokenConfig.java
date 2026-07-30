package com.aieducenter.aieducenteridentity.config;

import java.util.Collections;
import java.util.List;

import org.springframework.context.annotation.Configuration;

import cn.dev33.satoken.stp.StpInterface;

/**
 * Sa-Token 权限/角色 SPI 实现（平台不变式：本服务作为 IdP 自实现 StpInterface）。
 *
 * <p>当前 Phase 0：只 Account 一种身份、只用 {@code @RequireAuth}（checkLogin），
 * 权限/角色暂返回空。组织治理角色（owner/admin/member）留到 Phase 4 填充。</p>
 *
 * <p>本服务默认 loginType（{@code "login"}），无需多 {@code StpLogic}。</p>
 */
@Configuration
public class IdentitySaTokenConfig implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return Collections.emptyList();
    }
}
