package com.aieducenter.aieducenteridentity;

import com.cartisan.test.archunit.CartisanArchRules;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;

/**
 * 架构守护测试。
 *
 * <p>继承框架全套 ArchUnit 规则（分层 / 命名 / 禁用 / 编码规范），守护主代码（src/main）的
 * 六边形依赖方向与编码规范。{@link ImportOption.DoNotIncludeTests} 排除测试类
 * （测试类允许使用 {@code @Autowired} 字段注入 MockMvc 等，不纳入结构守护）。
 */
@AnalyzeClasses(packages = "com.aieducenter.aieducenteridentity", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureTest extends CartisanArchRules {
}
