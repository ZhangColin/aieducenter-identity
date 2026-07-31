package com.aieducenter.aieducenteridentity.sso.endpoints.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import com.aieducenter.aieducenteridentity.test.IdentityIntegrationTestBase;

/**
 * {@link SsoSessionFilter} 集成测试——受保护路径无 SSO 会话 → 401；非受保护路径放行（issue #15）。
 *
 * <p>「有效会话绑定 context + 放行」的端到端验证在 {@code SsoFlowIntegrationTest}（me/profile 去掉
 * {@code @RequireAuth} 之后）。</p>
 */
class SsoSessionFilterTest extends IdentityIntegrationTestBase {

    @Test
    void given_no_sso_cookie_when_access_protected_me_then_401_from_sso_filter() throws Exception {
        // 受保护路径无 SSO cookie → SsoSessionFilter 直接 401（带 SSO 标识消息）
        mvc.perform(get("/api/account/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("未登录或会话已过期")));
    }

    @Test
    void given_no_sso_cookie_when_access_public_endpoint_then_not_blocked_by_filter() throws Exception {
        // 非受保护路径（公开验证码接口）无 cookie 也放行 → 非 401
        MvcResult result = mvc.perform(get("/api/captcha")).andReturn();
        int status = result.getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401);
    }
}
