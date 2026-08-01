package com.aieducenter.demobff.sso;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
        if (txn == null || state == null || !txn.startsWith(state + ":")) {
            return redirect(props.getAppBaseUrl() + "/?error=state_mismatch");
        }
        TokenResponse tokens;
        try {
            tokens = oidcClient.exchangeCode(code);
        } catch (RuntimeException e) {
            // identity /token 非 2xx 等：不让其冒泡成 500，回前端带 error，并清 oauth_txn。
            response.addHeader("Set-Cookie", txnCookie("", 0).build().toString());
            return redirect(props.getAppBaseUrl() + "/?error=exchange_failed");
        }
        String sessionId = OidcClient.randomToken();
        sessionStore.put(sessionId, new BffSession(tokens.accessToken(), tokens.idToken(), tokens.refreshToken()));
        response.addHeader("Set-Cookie", sessionCookie(sessionId).build().toString());
        response.addHeader("Set-Cookie", txnCookie("", 0).build().toString()); // 清 oauth_txn
        return redirect(props.getAppBaseUrl() + "/");
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        Cookie existing = findCookie(request, "demo_session");
        if (existing != null) {
            sessionStore.remove(existing.getValue());
        }
        response.addHeader("Set-Cookie", sessionCookie("").maxAge(0).build().toString());
        return redirect(props.getAppBaseUrl() + "/");
    }

    private static ResponseEntity<Void> redirect(String location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(java.net.URI.create(location)).build();
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
            .httpOnly(true).secure(true).sameSite("Lax").path("/").maxAge(maxAge);
    }

    private ResponseCookie.ResponseCookieBuilder sessionCookie(String value) {
        return ResponseCookie.from("demo_session", value)
            .httpOnly(true).secure(true).sameSite("Lax").path("/");
    }
}
