package com.aieducenter.aieducenteridentity.account.endpoints.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.aieducenter.aieducenteridentity.account.config.ManagementProperties;
import com.cartisan.openapi.filter.SignatureVerificationFilter;
import com.cartisan.openapi.provider.ApiKeyInfo;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 管理调用方 gate 拦截器（ADR-0010 / #67）——与 cartisan-openapi 的
 * {@code SignatureVerificationInterceptor} 同模式（非 AOP 切面）。
 *
 * <p>仅对带 {@link RequireManagementCaller}（方法或类）的 handler 生效：读验签成功后
 * {@code SignatureVerificationFilter} 存入 request attribute 的 {@link ApiKeyInfo#apiKey()}
 * （app-registry 语义 {@code apiKey = app_code}，创建后不可变），不在
 * {@link ManagementProperties#getAuthorizedCallers()} 白名单 → 403。</p>
 *
 * <h3>为什么比对 apiKey 而非 callerAppName</h3>
 * <p>{@code RequestContext.getCallerAppName()} 装的是 app 的<b>显示名</b>（app-registry
 * {@code resolveApiKeyInfo} 取 {@code app.name}，如 admin-console 的「管理后台」）——显示名是人类文案、
 * 可随时改，作鉴权标识不可靠；白名单配的是 {@code app_code}（如 {@code admin-console}）。故比对
 * 稳定标识 {@code ApiKeyInfo.apiKey()}（= app_code，即入站 {@code X-Api-Key}，但必须取验签后的
 * attribute 而非裸 header——header 可伪造，attribute 仅验签通过才写入）。</p>
 *
 * <h3>签名通过后才判白名单</h3>
 * <p>attribute 缺失说明签名未通过（未签名 / 无 {@code X-Api-Key} / 验签失败）——
 * 此时<b>不在此 gate 判、直接放行</b>，交由 {@code @RequireSignature} 拦截器回 401（认证缺失）。
 * 故本 gate 只会把「签名已通过但调用方非白名单」的请求拦成 403，正确区分 401（未认证）与 403（未授权）。
 * 注册 order 在签名 interceptor 之后（见 {@link ManagementWebConfig}），二者配合：</p>
 * <ul>
 *   <li>未签名 → 签名 interceptor 401（本 gate 不运行或放行）；</li>
 *   <li>签名通过、非白名单 → 本 gate 403；</li>
 *   <li>签名通过、白名单内 → 放行。</li>
 * </ul>
 *
 * @since 0.1.0
 * @see RequireManagementCaller
 * @see ManagementProperties
 * @see ManagementWebConfig
 */
public class ManagementCallerInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(ManagementCallerInterceptor.class);

    private final ObjectMapper objectMapper;
    private final ManagementProperties properties;

    public ManagementCallerInterceptor(ObjectMapper objectMapper, ManagementProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireManagementCaller methodAnnotation = handlerMethod.getMethodAnnotation(RequireManagementCaller.class);
        RequireManagementCaller classAnnotation =
            handlerMethod.getBeanType().getAnnotation(RequireManagementCaller.class);
        if (methodAnnotation == null && classAnnotation == null) {
            return true;
        }

        // 验签成功后 filter 存入的 ApiKeyInfo——apiKey() = app_code（稳定标识，勿用可变显示名 appName()）
        Object attribute = request.getAttribute(SignatureVerificationFilter.API_KEY_INFO_ATTR);
        // 签名未通过（attribute 缺失）→ 交由 @RequireSignature 拦截器回 401，此处不重复判。
        if (!(attribute instanceof ApiKeyInfo apiKeyInfo)) {
            return true;
        }
        String callerAppId = apiKeyInfo.apiKey();
        if (!properties.isAuthorized(callerAppId)) {
            writeError(response, 403,
                "Management caller required: " + callerAppId + " is not authorized.");
            return false;
        }
        return true;
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        log.warn("Management caller rejected: {}", message);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = Map.of(
            "code", status,
            "message", message,
            "success", false
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
