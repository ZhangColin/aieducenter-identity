package com.aieducenter.aieducenteridentity.test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 测试用 HMAC-SHA256 签名工具，与 cartisan-openapi {@code SignatureVerificationFilter} 的签名算法
 * <strong>完全一致</strong>（复制自 payment / app-registry 的同名工具，issue #58）。
 *
 * <p>验签是签名服务（account / verification bc 的 {@code @RequireSignature} 端点，#56）的安全闸，
 * 本工具在集成测试里扮演「可信调用方」——对请求体计算 5 个签名头，走真实的
 * filter → {@code RemoteApiKeyProvider} → interceptor → controller 路径。</p>
 *
 * <p>用法：
 * <pre>{@code
 *   TestSignatureHelper signer = new TestSignatureHelper(apiKey, apiSecret);
 *   Map<String, String> headers = signer.sign(jsonBody);
 *   mvc.perform(post("/api/account/authenticate").contentType(JSON).content(jsonBody)
 *       .headers(new HttpHeaders(headers))) ... // 或逐个 .header(k, v)
 * }</pre>
 *
 * @since 0.1.0
 */
public final class TestSignatureHelper {

    public static final String HEADER_API_KEY = "X-Api-Key";
    public static final String HEADER_TIMESTAMP = "X-Timestamp";
    public static final String HEADER_NONCE = "X-Nonce";
    public static final String HEADER_BODY_DIGEST = "X-Body-Digest";
    public static final String HEADER_SIGN = "X-Sign";

    private final String apiKey;
    private final String apiSecret;

    public TestSignatureHelper(String apiKey, String apiSecret) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    /**
     * 对请求体签名，返回 5 个签名头。
     *
     * @param bodyContent 请求体内容（GET 请求传 {@code null} 或空串）
     * @return 签名头（key → value）
     */
    public Map<String, String> sign(String bodyContent) {
        byte[] bodyBytes = bodyContent != null ? bodyContent.getBytes(StandardCharsets.UTF_8) : new byte[0];
        String bodyDigest = sha256Hex(bodyBytes);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String nonce = UUID.randomUUID().toString();

        String stringToSign = buildStringToSign(apiKey, bodyDigest, nonce, timestamp);
        String sign = hmacSha256Hex(stringToSign, apiSecret);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_API_KEY, apiKey);
        headers.put(HEADER_TIMESTAMP, timestamp);
        headers.put(HEADER_NONCE, nonce);
        headers.put(HEADER_BODY_DIGEST, bodyDigest);
        headers.put(HEADER_SIGN, sign);
        return headers;
    }

    // ---- 与 SignatureVerificationFilter 逐字节对齐的签名串构造 ----
    // 覆盖 POST + 无 query string 场景（identity 签名端点本期皆如此）；framework 的 queryParams 恒为空。

    static String buildStringToSign(String apiKey, String bodyDigest, String nonce, String timestamp) {
        TreeMap<String, String> sorted = new TreeMap<>();
        sorted.put("apiKey", apiKey);
        sorted.put("bodyDigest", bodyDigest);
        sorted.put("nonce", nonce);
        sorted.put("timestamp", timestamp);

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
