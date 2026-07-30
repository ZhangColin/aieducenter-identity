package com.aieducenter.aieducenteridentity.account.domain.repository;

import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.cartisan.core.stereotype.Port;
import com.cartisan.core.stereotype.PortType;
import com.cartisan.data.jpa.repository.BaseRepository;

/**
 * 个人资料仓储。主键为 userId，与 {@link AccountRepository} 的账号 1:1。
 *
 * @since 0.1.0
 */
@Port(PortType.REPOSITORY)
public interface ProfileRepository extends BaseRepository<Profile, Long> {
}
