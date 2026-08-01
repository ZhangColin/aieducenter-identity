package com.aieducenter.demobff.sso;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * demo BFF /api/me（issue #16）：凭业务 cookie 取会话 → 解 id_token claims 返当前用户。
 * 无有效会话 → 401。
 */
@RestController
public class MeController {

    private final OidcClient oidcClient;
    private final BffSessionStore sessionStore;

    public MeController(OidcClient oidcClient, BffSessionStore sessionStore) {
        this.oidcClient = oidcClient;
        this.sessionStore = sessionStore;
    }

    @GetMapping("/api/me")
    public ResponseEntity<Map<String, String>> me(@CookieValue(value = "demo_session", required = false) String sid) {
        if (sid == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return sessionStore.get(sid)
            .map(s -> {
                OidcClient.IdTokenClaims c = oidcClient.decodeIdToken(s.idToken());
                return ResponseEntity.ok(Map.of(
                    "userId", n(c.subject()),
                    "email", n(c.email()),
                    "nickname", n(c.nickname()),
                    "picture", n(c.picture())));
            })
            .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private static String n(String v) {
        return v == null ? "" : v;
    }
}
