package com.aieducenter.demobff.sso;

/**
 * BFF 服务端会话——存换到的 token 三件套（浏览器永不接触）。
 */
public record BffSession(String accessToken, String idToken, String refreshToken) {
}
