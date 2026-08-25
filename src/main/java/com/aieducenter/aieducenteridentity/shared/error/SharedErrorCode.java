package com.aieducenter.aieducenteridentity.shared.error;

import com.cartisan.core.exception.CodeMessage;

/**
 * 跨上下文共享错误码——应用级对外 wire 契约的单一常量点。
 *
 * <h3>为什么存在</h3>
 * <p>cartisan-web 的 {@code CodeMessageRegistry}（470f787 起启动期全应用扫描）强制
 * <b>一个 code 串只有一个 CodeMessage 常量</b>（同串不同常量即启动失败，防语义二义）。
 * 而 {@code VERIFICATION_CODE_INVALID} 的对外出口天然跨上下文：</p>
 * <ul>
 *   <li>verification 直抛（验码失败），经 sso / account 的 adapter 以 {@code DomainException}
 *       形态<b>透传</b>；</li>
 *   <li>sso 自家抛出点（register 必填码兜底、login-code 账号不存在翻译）。</li>
 * </ul>
 * <p>该串因此不是任何单一上下文的私有物，收归本中立契约位置、双方共引——
 * ADR-0007 禁的是跨上下文引用<b>对方 domain</b> 错误（上下间耦合），双方共引中立契约不在此列。</p>
 *
 * <h3>防用户枚举不变量（#54 延续）</h3>
 * <p>login-code 路径要求「错码」与「账号不存在」回<b>完全相同</b>的 code + message + status。
 * 原先两来源各自持串（sso 复制 verification 的串），现统一为同串<b>同对象</b>，
 * 防枚举一致性由编译期保证，不再靠两处定义逐字符对齐。</p>
 *
 * <p>wire 契约逐字符不变（#54「前端契约无感」承诺延续）。</p>
 *
 * @since 0.1.0
 */
public enum SharedErrorCode implements CodeMessage {

    /** 验证码错误（register 必填码兜底 / login-code 错码透传与账号不存在翻译 / account 验码透传——同串同对象）。 */
    VERIFICATION_CODE_INVALID("VERIFICATION_CODE_INVALID", "验证码错误", 400);

    private final String code;
    private final String message;
    private final int httpStatus;

    SharedErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}
