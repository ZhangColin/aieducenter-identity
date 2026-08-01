package com.aieducenter.aieducenteridentity;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.cartisan.test.archunit.CartisanArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 架构守护测试。
 *
 * <p>继承框架全套 ArchUnit 规则（分层 / 命名 / 禁用 / 编码规范），守护主代码（src/main）的
 * 六边形依赖方向与编码规范。{@link ImportOption.DoNotIncludeTests} 排除测试类
 * （测试类允许使用 {@code @Autowired} 字段注入 MockMvc 等，不纳入结构守护）。
 */
@AnalyzeClasses(packages = "com.aieducenter.aieducenteridentity", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest extends CartisanArchRules {

    /**
     * ADR-0004 / issue #21：identity 弃 sa-token / cartisan-security，全库只剩一套 SSO 会话——
     * 主代码不得再依赖 sa-token（{@code cn.dev33.satoken}）或 {@code com.cartisan.security}。
     */
    @ArchTest
    static final ArchRule noSaTokenNorCartisanSecurity = noClasses()
        .should().dependOnClassesThat()
        .resideInAnyPackage("cn.dev33.satoken..", "com.cartisan.security..")
        .because("ADR-0004：identity 弃 sa-token / cartisan-security，受保护接口凭 SSO cookie 认人（issue #21）");
}
