package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import java.net.URI;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.aieducenteridentity.sso.application.SsoLoginAppService;
import com.aieducenter.aieducenteridentity.sso.application.SsoLoginCodeAppService;
import com.aieducenter.aieducenteridentity.sso.application.SsoRegisterAppService;
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByCodeSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.LoginByPasswordSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.RegisterSsoCommand;
import com.aieducenter.aieducenteridentity.sso.application.dto.SsoLoginResult;
import com.aieducenter.aieducenteridentity.sso.endpoints.web.SsoCookieService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 认证入口（{@code /api/auth/*}）——建 SSO 会话 + 发 code（CONTEXT 接口命名空间 / issue #15、#18）。
 *
 * <p>密码登录：验凭据 → 建 SSO 会话 + 种 cookie → 发 code → 302 回 {@code redirect_uri?code&state}。
 * 密码注册：唯一性校验 → 建号 → 注册即登录（同一后半段：建会话 + 种 cookie + 发 code + 302）。
 * 登录/注册失败抛领域异常，留登录页显示（不回业务应用）。</p>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "SSO / Auth", description = "认证入口（建会话发 code）")
public class AuthController {

    private final SsoLoginAppService loginService;
    private final SsoLoginCodeAppService loginCodeService;
    private final SsoRegisterAppService registerService;
    private final SsoCookieService cookieService;

    public AuthController(SsoLoginAppService loginService, SsoLoginCodeAppService loginCodeService,
            SsoRegisterAppService registerService, SsoCookieService cookieService) {
        this.loginService = loginService;
        this.loginCodeService = loginCodeService;
        this.registerService = registerService;
        this.cookieService = cookieService;
    }

    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "密码登录", description = "验凭据 → 建 SSO 会话 + 种 cookie + 发 code + 302 回 redirect_uri")
    public ResponseEntity<Void> loginByPassword(@Valid @RequestBody LoginByPasswordSsoCommand command,
            HttpServletResponse response) {
        return respond(loginService.loginByPassword(command), response);
    }

    /**
     * form 顶层提交入口（identity-web 登录页，issue #23）。
     *
     * <p>SPA fetch 收到 302 会自动跟随且拿不到 Location（跨域跟随被 CORS 拦死）；原生 form 提交让
     * 浏览器顶层导航自然跟随 302——SSO 链路保持「跨站全后端 302 + 浏览器导航、零跨域 fetch」。
     * 与 JSON 入口同一契约（字段名同为 camelCase，form 只是编码差异）、同一 service。</p>
     */
    @PostMapping(value = "/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "密码登录（form 提交）", description = "同 JSON 登录，供浏览器原生 form 顶层提交")
    public ResponseEntity<Void> loginByPasswordForm(@Valid @ModelAttribute LoginByPasswordSsoCommand command,
            HttpServletResponse response) {
        return respond(loginService.loginByPassword(command), response);
    }

    @PostMapping(value = "/login-code", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "验证码登录", description = "email/phone + 验证码（LOGIN 用途）→ 建 SSO 会话 + 种 cookie + 发 code + 302 回 redirect_uri；账号不存在与验证码错误同一响应（防枚举）")
    public ResponseEntity<Void> loginByCode(@Valid @RequestBody LoginByCodeSsoCommand command,
            HttpServletResponse response) {
        return respond(loginCodeService.loginByCode(command), response);
    }

    /** form 顶层提交入口（identity-web 登录页）——同 {@link #loginByPasswordForm} 的动机，同一契约。 */
    @PostMapping(value = "/login-code", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "验证码登录（form 提交）", description = "同 JSON 验证码登录，供浏览器原生 form 顶层提交")
    public ResponseEntity<Void> loginByCodeForm(@Valid @ModelAttribute LoginByCodeSsoCommand command,
            HttpServletResponse response) {
        return respond(loginCodeService.loginByCode(command), response);
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "注册", description = "唯一性校验 → 联络方式当场验码（验不过不建号）→ 建号（密码可选）→ 注册即登录（建 SSO 会话 + 种 cookie + 发 code + 302 回 redirect_uri）")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterSsoCommand command,
            HttpServletResponse response) {
        return respond(registerService.register(command), response);
    }

    /** form 顶层提交入口（identity-web 注册页）——同 {@link #loginByPasswordForm} 的动机，同一契约。 */
    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "注册（form 提交）", description = "同 JSON 注册，供浏览器原生 form 顶层提交")
    public ResponseEntity<Void> registerForm(@Valid @ModelAttribute RegisterSsoCommand command,
            HttpServletResponse response) {
        return respond(registerService.register(command), response);
    }

    /** 认证成功统一响应：种 SSO cookie + 302 回 {@code redirect_uri?code&state}。 */
    private ResponseEntity<Void> respond(SsoLoginResult result, HttpServletResponse response) {
        cookieService.setSessionCookie(response, result.sessionId());
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(result.redirectUrl())).build();
    }
}
