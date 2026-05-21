package com.offerlab.community.archtest;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 模块间依赖约束（ArchUnit 编译期校验）
 */
@AnalyzeClasses(packages = "com.offerlab.community")
class ArchitectureTest {

    /** 规则 1：interaction 不能依赖 feed 的实现细节 */
    @ArchTest
    static final ArchRule interaction_should_not_depend_on_feed_internals =
            noClasses()
                    .that().resideInAPackage("..interaction..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "..feed.application..",
                            "..feed.domain..",
                            "..feed.infrastructure.."
                    );

    /** 规则 2：controller 不能直接访问 mapper */
    @ArchTest
    static final ArchRule controller_should_not_access_mapper =
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..persistence.mapper..");

    /** 规则 3：domain.model 不能依赖 Spring */
    @ArchTest
    static final ArchRule domain_model_should_not_depend_on_spring =
            noClasses()
                    .that().resideInAPackage("..domain.model..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("org.springframework..");
}
