package com.aieducenter.aieducenteridentity.sso.endpoints.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.aieducenter.aieducenteridentity.verification.application.VerificationCodeAppService;
import com.aieducenter.aieducenteridentity.verification.application.dto.SendCodeResponse;
import com.aieducenter.aieducenteridentity.verification.application.dto.VerifyCodeResult;

/**
 * {@link SsoVerificationCodeController} 切片测试——{@code /api/sso/verification-code/*}（issue #55 迁 namespace）。
 */
@WebMvcTest(controllers = SsoVerificationCodeController.class)
class SsoVerificationCodeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VerificationCodeAppService service;

    @Test
    void given_valid_request_when_send_verification_code_then_return_success() throws Exception {
        // Given
        when(service.sendEmailVerificationCode(any(), eq("127.0.0.1")))
            .thenReturn(new SendCodeResponse(300, 60));

        // When & Then - Controller显式返回ApiResponse
        mvc.perform(post("/api/sso/verification-code/email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.expireInSeconds").value(300))
            .andExpect(jsonPath("$.data.resentAfterSeconds").value(60));
    }

    @Test
    void given_valid_code_when_verify_code_then_return_verified() throws Exception {
        // Given
        when(service.verifyCode(any()))
            .thenReturn(new VerifyCodeResult(true, "验证码正确"));

        // When & Then - Controller显式返回ApiResponse
        mvc.perform(post("/api/sso/verification-code/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"code\":\"123456\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.verified").value(true));
    }

    @Test
    void given_trusted_proxy_with_forwarded_header_when_send_code_then_uses_forwarded_ip() throws Exception {
        // Given — request from 127.0.0.1 (loopback proxy) with X-Forwarded-For
        when(service.sendEmailVerificationCode(any(), eq("203.0.113.5")))
            .thenReturn(new SendCodeResponse(300, 60));

        // When & Then
        mvc.perform(post("/api/sso/verification-code/email")
                .with(request -> { request.setRemoteAddr("127.0.0.1"); return request; })
                .header("X-Forwarded-For", "203.0.113.5")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isOk());

        verify(service).sendEmailVerificationCode(any(), eq("203.0.113.5"));
    }

    @Test
    void given_trusted_proxy_with_multiple_forwarded_ips_when_send_code_then_uses_first_ip() throws Exception {
        // Given — X-Forwarded-For with chain; should use first (original client)
        when(service.sendEmailVerificationCode(any(), eq("203.0.113.5")))
            .thenReturn(new SendCodeResponse(300, 60));

        mvc.perform(post("/api/sso/verification-code/email")
                .with(request -> { request.setRemoteAddr("10.0.0.1"); return request; })
                .header("X-Forwarded-For", "203.0.113.5, 10.0.0.2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isOk());

        verify(service).sendEmailVerificationCode(any(), eq("203.0.113.5"));
    }

    @Test
    void given_non_trusted_proxy_with_forwarded_header_when_send_code_then_uses_remote_addr() throws Exception {
        // Given — request from public IP (not trusted proxy); X-Forwarded-For should be ignored
        String publicIp = "203.0.113.1";
        when(service.sendEmailVerificationCode(any(), eq(publicIp)))
            .thenReturn(new SendCodeResponse(300, 60));

        mvc.perform(post("/api/sso/verification-code/email")
                .with(request -> { request.setRemoteAddr(publicIp); return request; })
                .header("X-Forwarded-For", "1.2.3.4")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isOk());

        verify(service).sendEmailVerificationCode(any(), eq(publicIp));
    }

    @Test
    void given_trusted_proxy_172_16_range_with_forwarded_header_when_send_code_then_uses_forwarded_ip() throws Exception {
        // Given — 172.16.x.x is in the private range
        when(service.sendEmailVerificationCode(any(), eq("203.0.113.9")))
            .thenReturn(new SendCodeResponse(300, 60));

        mvc.perform(post("/api/sso/verification-code/email")
                .with(request -> { request.setRemoteAddr("172.16.0.1"); return request; })
                .header("X-Forwarded-For", "203.0.113.9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isOk());

        verify(service).sendEmailVerificationCode(any(), eq("203.0.113.9"));
    }

    @Test
    void given_172_32_address_when_send_code_then_not_treated_as_trusted_proxy() throws Exception {
        // Given — 172.32.x.x is outside the 172.16-31 private range
        String ip172_32 = "172.32.0.1";
        when(service.sendEmailVerificationCode(any(), eq(ip172_32)))
            .thenReturn(new SendCodeResponse(300, 60));

        mvc.perform(post("/api/sso/verification-code/email")
                .with(request -> { request.setRemoteAddr(ip172_32); return request; })
                .header("X-Forwarded-For", "9.9.9.9")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isOk());

        verify(service).sendEmailVerificationCode(any(), eq(ip172_32));
    }

    @Test
    void given_trusted_proxy_with_no_forwarded_header_when_send_code_then_uses_remote_addr() throws Exception {
        // Given — trusted proxy but no X-Forwarded-For header
        when(service.sendEmailVerificationCode(any(), eq("192.168.1.1")))
            .thenReturn(new SendCodeResponse(300, 60));

        mvc.perform(post("/api/sso/verification-code/email")
                .with(request -> { request.setRemoteAddr("192.168.1.1"); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"purpose\":\"REGISTER\"}"))
            .andExpect(status().isOk());

        verify(service).sendEmailVerificationCode(any(), eq("192.168.1.1"));
    }
}
