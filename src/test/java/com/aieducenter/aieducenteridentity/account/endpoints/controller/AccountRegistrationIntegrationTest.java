package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.cartisan.test.base.ApiTestAssertions;

/**
 * 注册流程 HTTP 黑盒集成测试。
 *
 * <p>覆盖：手机号+码注册、邮箱+码注册、验过才建号（错码不建）、查重 409、至少一联络方式、
 * 纯验证码（无密码）建号、建号即登录返回 accessToken。</p>
 */
@Transactional
class AccountRegistrationIntegrationTest extends AccountIntegrationTestBase {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Test
    void given_phone_and_sms_code_when_register_then_account_and_profile_created_and_token_returned() throws Exception {
        String phone = "13800138001";
        Captcha captcha = getCaptcha();
        String code = sendSmsCode(phone, "REGISTER", captcha);

        MvcResult result = mvc.perform(post("/api/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPhoneBody(phone, "Password123", "Alice", code)))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andReturn();

        // DB 副作用：account + profile 建好
        Account account = accountRepository.findByPhone(phone).orElseThrow();
        assertThat(account.getPasswordHash()).isNotBlank();
        Profile profile = profileRepository.findById(account.getId()).orElseThrow();
        assertThat(profile.getNickname()).isEqualTo("Alice");
        // accessToken 非空且即登录
        assertThat(extractAccessToken(result)).isNotBlank();
    }

    @Test
    void given_email_and_email_code_when_register_then_account_created() throws Exception {
        String email = "bob@example.com";
        String code = sendEmailCode(email, "REGISTER");

        mvc.perform(post("/api/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerEmailBody(email, "Password123", null, code)))
            .andExpect(ApiTestAssertions.assertOk())
            .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

        assertThat(accountRepository.findByEmail(email)).isPresent();
    }

    @Test
    void given_email_and_phone_both_contacts_when_register_then_both_stored() throws Exception {
        String email = "dual@example.com";
        String phone = "13800138010";
        String emailCode = sendEmailCode(email, "REGISTER");
        String smsCode = sendSmsCode(phone, "REGISTER", getCaptcha());

        mvc.perform(post("/api/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"phone\":\"" + phone + "\","
                    + "\"password\":\"Password123\",\"nickname\":\"Dual\","
                    + "\"emailVerificationCode\":\"" + emailCode + "\","
                    + "\"smsVerificationCode\":\"" + smsCode + "\"}"))
            .andExpect(ApiTestAssertions.assertOk());

        Account account = accountRepository.findByEmail(email).orElseThrow();
        assertThat(account.getPhone()).isEqualTo(phone);
    }

    @Test
    void given_passwordless_phone_register_when_register_then_password_hash_null() throws Exception {
        String phone = "13800138002";
        String code = sendSmsCode(phone, "REGISTER", getCaptcha());

        mvc.perform(post("/api/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPhoneBody(phone, null, null, code)))
            .andExpect(ApiTestAssertions.assertOk());

        Account account = accountRepository.findByPhone(phone).orElseThrow();
        assertThat(account.getPasswordHash()).isNull();
    }

    @Test
    void given_duplicate_phone_when_register_then_409() throws Exception {
        String phone = "13800138003";
        // given — 该手机号已建号（直接落库，避免二次发码撞 60s 冷却）
        Account existing = Account.register(null, phone, "hashed-pw");
        accountRepository.save(existing);

        // when — 同手机号注册（一次性发码）→ 验码通过后查重命中 → 409
        String code = sendSmsCode(phone, "REGISTER", getCaptcha());
        mvc.perform(post("/api/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPhoneBody(phone, "Password123", null, code)))
            .andExpect(status().isConflict())
            .andExpect(ApiTestAssertions.assertError(409));
    }

    @Test
    void given_no_contact_when_register_then_400() throws Exception {
        mvc.perform(post("/api/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"Ghost\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));
    }

    @Test
    void given_wrong_sms_code_when_register_then_400_and_no_account() throws Exception {
        String phone = "13800138004";
        // 不发码直接用错码注册 → 验证失败 400，不建号
        mvc.perform(post("/api/account/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerPhoneBody(phone, "Password123", null, "000000")))
            .andExpect(status().isBadRequest())
            .andExpect(ApiTestAssertions.assertError(400));

        assertThat(accountRepository.findByPhone(phone)).isEmpty();
    }

    private static String registerPhoneBody(String phone, String password, String nickname, String smsCode) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"phone\":\"").append(phone).append("\"");
        if (password != null) sb.append(",\"password\":\"").append(password).append("\"");
        if (nickname != null) sb.append(",\"nickname\":\"").append(nickname).append("\"");
        sb.append(",\"smsVerificationCode\":\"").append(smsCode).append("\"");
        sb.append("}");
        return sb.toString();
    }

    private static String registerEmailBody(String email, String password, String nickname, String emailCode) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"email\":\"").append(email).append("\"");
        if (password != null) sb.append(",\"password\":\"").append(password).append("\"");
        if (nickname != null) sb.append(",\"nickname\":\"").append(nickname).append("\"");
        sb.append(",\"emailVerificationCode\":\"").append(emailCode).append("\"");
        sb.append("}");
        return sb.toString();
    }
}
