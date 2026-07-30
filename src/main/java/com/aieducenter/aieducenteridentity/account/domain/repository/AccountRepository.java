package com.aieducenter.aieducenteridentity.account.domain.repository;

import java.util.Optional;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;
import com.cartisan.data.jpa.repository.BaseRepository;

/**
 * 账号仓储。
 *
 * <p>查询方法自动排除已软删记录（{@code act_account} 软删读过滤）；查重也只看未删记录。</p>
 *
 * @since 0.1.0
 */
@Port(PortType.REPOSITORY)
public interface AccountRepository extends BaseRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    Optional<Account> findByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);
}
