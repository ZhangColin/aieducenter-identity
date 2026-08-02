package com.aieducenter.aieducenteridentity.account.domain.aggregate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.cartisan.core.exception.DomainException;

/**
 * {@link Account#register} 建号不变量（issue #18：注册入口格式校验收进聚合，任何入口建号都生效）。
 */
class AccountTest {

    @Test
    void given_malformed_email_when_register_then_email_invalid() {
        assertThatThrownBy(() -> Account.register("not-an-email", null, "hash"))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.EMAIL_INVALID);
    }

    @Test
    void given_malformed_phone_when_register_then_phone_invalid() {
        assertThatThrownBy(() -> Account.register(null, "12345", "hash"))
            .isInstanceOf(DomainException.class)
            .extracting(ex -> ((DomainException) ex).getCodeMessage())
            .isEqualTo(AccountError.PHONE_INVALID);
    }
}
