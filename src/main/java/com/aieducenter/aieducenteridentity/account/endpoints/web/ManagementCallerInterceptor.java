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
import com.cartisan.core.context.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 管理调用方 gate 拦截器（ADR-0010 / #67）——与 cartisan-openapi 的
 * {@code SignatureVerificationInterceptor} 同模式（非 AOP 切面）。
 *
 * <p>仅对带 {@link RequireManagementCaller}（方法或类）的 handler 生效：读
 * {@code RequestContext.getCallerAppName()}（签名 filter 验签成功后 {@code withCaller} 填充），
 * 不在 {@link ManagementProperties#getAuthorizedCallers()} 白名单 → 403。</p>
 *
 * <h3>签名通过后才判白名单</h3>
 * <p>{@code callerAppName} 为 {@code null} 说明签名未通过（未签名 / 无 {@code X-Api-Key}）——
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

        String callerAppName = RequestContext.getCallerAppName();
        // 签名未通过（callerAppName 为空）→ 交由 @RequireSignature 拦截器回 401，此处不重复判。
        if (callerAppName == null) {
            return true;
        }
        if (!properties.isAuthorized(callerAppName)) {
            writeError(response, 403,
                "Management caller required: " + callerAppName + " is not authorized.");
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
