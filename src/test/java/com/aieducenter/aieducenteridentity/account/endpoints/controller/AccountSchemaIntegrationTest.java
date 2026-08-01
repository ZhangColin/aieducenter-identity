package com.aieducenter.aieducenteridentity.account.endpoints.controller;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;

/**
 * Account schema 守护测试——验 ADR-0001 / acceptance 的 schema 落地：
 * <ul>
 *   <li>account 主表无 username 列（丢掉 studio 登录键）</li>
 *   <li>password_hash 可空（社交/纯验证码账号无密码）</li>
 *   <li>软删部分唯一索引：软删后同邮箱可重新注册（不撞唯一约束）</li>
 * </ul>
 */
@Transactional
class AccountSchemaIntegrationTest extends AccountIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void act_account_has_no_username_column() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.columns "
                + "WHERE table_name = 'act_account' AND column_name = 'username'",
            Integer.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void password_hash_column_is_nullable() {
        String nullable = jdbcTemplate.queryForObject(
            "SELECT is_nullable FROM information_schema.columns "
                + "WHERE table_name = 'act_account' AND column_name = 'password_hash'",
            String.class);
        assertThat(nullable).isEqualTo("YES");
    }

    @Test
    void soft_deleted_email_can_be_re_registered_via_partial_unique_index() {
        String email = "recycle@example.com";
        // given — 先建号再软删
        Account first = Account.register(email, null, "hashed-pw");
        accountRepository.save(first);
        accountRepository.delete(first); // 软删（deleted=true）

        // 软删生效（顺带把软删 UPDATE 先刷进库——Hibernate flush 插入先于更新，
        // 不隔开的话下面的新 INSERT 会撞部分唯一索引）
        assertThat(accountRepository.findByEmail(email)).isEmpty();

        // when — 同邮箱重建账号 → 不撞唯一约束（注册 HTTP 入口随旧链路拆除，约束本身是 schema 级）
        accountRepository.save(Account.register(email, null, "hashed-pw-2"));

        // then — 新账号建好（旧软删记录仍在，但被部分唯一索引排除）
        assertThat(accountRepository.findByEmail(email)).isPresent();
    }
}
