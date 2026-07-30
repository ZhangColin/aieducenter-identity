package com.aieducenter.aieducenteridentity.account.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aieducenter.aieducenteridentity.account.application.dto.command.UpdateProfileCommand;
import com.aieducenter.aieducenteridentity.account.application.dto.response.AccountProfileResponse;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Account;
import com.aieducenter.aieducenteridentity.account.domain.aggregate.Profile;
import com.aieducenter.aieducenteridentity.account.domain.error.AccountError;
import com.aieducenter.aieducenteridentity.account.domain.repository.AccountRepository;
import com.aieducenter.aieducenteridentity.account.domain.repository.ProfileRepository;
import com.cartisan.core.context.RequestContext;
import com.cartisan.core.exception.DomainException;

/**
 * 个人资料应用服务——查看 / 编辑当前登录用户的 profile（需登录态）。
 *
 * @since 0.1.0
 */
@Service
public class AccountProfileAppService {

    private final AccountRepository accountRepository;
    private final ProfileRepository profileRepository;

    public AccountProfileAppService(AccountRepository accountRepository, ProfileRepository profileRepository) {
        this.accountRepository = accountRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * 查看当前登录用户的资料（account + profile 合并视图）。
     *
     * @throws DomainException USER_NOT_FOUND（登录态 userId 无对应账号）
     */
    @Transactional(readOnly = true)
    public AccountProfileResponse getCurrentProfile() {
        Long userId = RequestContext.getUserId();
        Account account = accountRepository.findById(userId)
            .orElseThrow(() -> new DomainException(AccountError.USER_NOT_FOUND));

        Profile profile = profileRepository.findById(userId).orElse(null);
        String nickname = profile != null ? profile.getNickname() : null;
        String avatar = profile != null ? profile.getAvatar() : null;

        return new AccountProfileResponse(
            account.getId(), account.getEmail(), account.getPhone(), nickname, avatar);
    }

    /**
     * 编辑当前登录用户的资料（仅更新非空字段）。
     *
     * @throws DomainException USER_NOT_FOUND（登录态 userId 无对应账号）
     */
    public void updateCurrentProfile(UpdateProfileCommand command) {
        Long userId = RequestContext.getUserId();
        accountRepository.findById(userId)
            .orElseThrow(() -> new DomainException(AccountError.USER_NOT_FOUND));

        Profile profile = profileRepository.findById(userId)
            .map(p -> {
                p.update(command.nickname(), command.avatar());
                return p;
            })
            .orElseGet(() -> Profile.create(userId, command.nickname(), command.avatar()));
        profileRepository.save(profile);
    }
}
