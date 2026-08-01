package com.aieducenter.demobff.sso;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * BFF 内存会话存储（demo 用，进程重启即丢）。key = 不透明业务 sessionId（业务 cookie 值）。
 */
@Component
public class BffSessionStore {

    private final ConcurrentHashMap<String, BffSession> store = new ConcurrentHashMap<>();

    public void put(String sessionId, BffSession session) {
        store.put(sessionId, session);
    }

    public Optional<BffSession> get(String sessionId) {
        return Optional.ofNullable(store.get(sessionId));
    }

    public void remove(String sessionId) {
        store.remove(sessionId);
    }
}
