package com.aieducenter.aieducenteridentity.sso.endpoints.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.aieducenter.aieducenteridentity.sso.domain.error.OidcException;

/**
 * OIDC 协议错误响应——{@code {error, error_description}} + 状态码（区别于内部 {@code ApiResponse}）。
 *
 * <p>{@link OidcException} 非 {@code CartisanException}，cartisan 全局处理器不接管；本 advice 专职处理。
 * 适用 {@code /authorize}（重定向前错）与 {@code /token}（400/401 + invalid_*）。</p>
 *
 * @since 0.1.0
 */
@RestControllerAdvice
public class OidcExceptionHandler {

    @ExceptionHandler(OidcException.class)
    public ResponseEntity<Map<String, String>> handle(OidcException ex) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", ex.error().code());
        body.put("error_description", ex.description());
        return ResponseEntity.status(HttpStatus.valueOf(ex.error().httpStatus())).body(body);
    }
}
