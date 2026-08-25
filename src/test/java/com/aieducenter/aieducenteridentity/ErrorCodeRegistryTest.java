package com.aieducenter.aieducenteridentity;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

import com.cartisan.web.doc.CodeMessageRegistry;

/**
 * 错误码注册表守护测试：应用全部 CodeMessage 枚举经 registry 扫描不冲突。
 *
 * <p>镜像 {@code CartisanWebAutoConfiguration#codeMessageRegistry} 的装配调用
 * （缺省扫描应用主包）。同 code 串映射到不同常量会在启动期由
 * {@code CodeMessageRegistry.register} 直接失败——本测试把该失败前移到单测：
 * 任何上下文再引入重复错误码串，这里秒级变红，不用等应用启动炸。</p>
 */
class ErrorCodeRegistryTest {

    @Test
    void allErrorCodesRegisterWithoutConflict() {
        assertThatCode(() -> CodeMessageRegistry.scan("com.aieducenter.aieducenteridentity"))
            .doesNotThrowAnyException();
    }
}
