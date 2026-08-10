package com.aieducenter.demobff.sso;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aieducenter.demobff.config.SsoProperties;

/**
 * demo BFF 认证端点（issue #16）：/auth/login 发起、/auth/callback 换 token+种业务 cookie、/auth/logout。
 * token 只存服务端会话；浏览器只持 demo_session 业务 cookie。
 */
@RestController
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private static final int TXN_MAX_AGE = 600;

    // 包级可见、非 final：便于 standalone 测试（MockMvcBuilders.standaloneSetup）直接注入 SsoProperties。
    SsoProperties props;
    private final OidcClient oidcClient;
    private final BffSessionStore sessionStore;

    public AuthController(SsoProperties props, OidcClient oidcClient, BffSessionStore sessionStore) {
        this.props = props;
        this.oidcClient = oidcClient;
        this.sessionStore = sessionStore;
    }

    @GetMapping("/auth/login")
    public ResponseEntity<Void> login(HttpServletResponse response) {
        String state = OidcClient.randomToken();
        String nonce = OidcClient.randomToken();
        response.addHeader("Set-Cookie", txnCookie(state + ":" + nonce, TXN_MAX_AGE).build().toString());
        return ResponseEntity.status(HttpStatus.FOUND)
            .location(java.net.URI.create(oidcClient.authorizeUrl(state, nonce))).build();
    }

    @GetMapping("/auth/callback")
    public ResponseEntity<Void> callback(@RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            @CookieValue(value = "oauth_txn", required = false) String txn,
            HttpServletResponse response) {
        if (txn == null || state == null || !state.equals(expectedStateOf(txn))) {
            return redirect(props.getAppBaseUrl() + "/?error=state_mismatch");
        }
        TokenResponse tokens;
        try {
            tokens = oidcClient.exchangeCode(code);
        } catch (TokenExchangeException e) {
            // identity /token 拒绝的具体原因（状态码 + {error, error_description}）打日志排查——
            // 此前被吞成笼统 exchange_failed，看不到 identity 的拒绝理由（issue #35）。
            log.warn("/token 换 token 失败：status={}, body={}", e.getHttpStatus(), e.getResponseBody());
            return rejectExchange(response);
        } catch (RuntimeException e) {
            // 兜底：未预期的异常也别冒泡成 500（不漏内部细节给前端）。
            log.warn("/token 换 token 失败（未预期异常）", e);
            return rejectExchange(response);
        }
        String sessionId = OidcClient.randomToken();
        sessionStore.put(sessionId, new BffSession(tokens.accessToken(), tokens.idToken(), tokens.refreshToken()));
        response.addHeader("Set-Cookie", sessionCookie(sessionId).build().toString());
        response.addHeader("Set-Cookie", txnCookie("", 0).build().toString()); // 清 oauth_txn
        return redirect(props.getAppBaseUrl() + "/");
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // 1. 清 demo 本地业务会话（BffSessionStore + demo_session cookie）
        Cookie existing = findCookie(request, "demo_session");
        if (existing != null) {
            sessionStore.remove(existing.getValue());
        }
        response.addHeader("Set-Cookie", sessionCookie("").maxAge(0).build().toString());
        // 2. 302 到 identity RP-Initiated Logout（issue #38）：浏览器顶层导航到 identity，清 SSO 会话 + sso_session cookie——
        //    否则 identity 侧 sso_session 仍在 → 下次 /authorize 直接发 code（二次免登）。
        //    identity 清完按 post_logout_redirect_uri 白名单 302 回 demo 首页（原样回带 state）。
        //    注意：post_logout_redirect_uri 需登记进 demo client 的 post_logout_redirect_uris 白名单（app-registry，独立于 redirect_uri，ADR-0005），否则 identity 清完会话返 200 不跳转。
        String base = props.getAppBaseUrl();
        String postLogoutRedirectUri = base.endsWith("/") ? base : base + "/";
        return redirect(oidcClient.logoutUrl(postLogoutRedirectUri, OidcClient.randomToken()));
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(location)).build();
    }

    /** 换 token 失败的统一兜底：清 oauth_txn、回前端笼统 exchange_failed（具体原因已在日志里）。 */
    private ResponseEntity<Void> rejectExchange(HttpServletResponse response) {
        response.addHeader("Set-Cookie", txnCookie("", 0).build().toString());
        return redirect(props.getAppBaseUrl() + "/?error=exchange_failed");
    }

    /**
     * 从 oauth_txn cookie（"<state>:<nonce>"）切出 state 段做精确比对；nonce 段留给 #17 校验 id_token。
     * 无冒号分隔符 → 视为非法，返回 null 触发 state_mismatch。
     */
    private static String expectedStateOf(String txn) {
        int idx = txn.indexOf(':');
        return idx < 0 ? null : txn.substring(0, idx);
    }

    private static Cookie findCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c;
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder txnCookie(String value, long maxAge) {
        return ResponseCookie.from("oauth_txn", value)
            .httpOnly(true).secure(props.isCookieSecure()).sameSite("Lax").path("/").maxAge(maxAge);
    }

    private ResponseCookie.ResponseCookieBuilder sessionCookie(String value) {
        return ResponseCookie.from("demo_session", value)
            .httpOnly(true).secure(props.isCookieSecure()).sameSite("Lax").path("/");
    }
}
