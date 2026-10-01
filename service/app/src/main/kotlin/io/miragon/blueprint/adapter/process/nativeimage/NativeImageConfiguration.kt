package io.miragon.blueprint.adapter.process.nativeimage

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportRuntimeHints

/**
 * Everything the embedded engine needs to run as a GraalVM native image: the reachability metadata
 * contributed to Spring's AOT processing, and the replacement for the one starter bean that relies on
 * `java.io.File`.
 */
@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(
    CibSevenRuntimeHints::class,
    MyBatisRuntimeHints::class,
    JerseyRuntimeHints::class,
    EnversRuntimeHints::class,
    ProcessModelRuntimeHints::class,
)
class NativeImageConfiguration {

    @Bean
    fun camundaDeploymentConfiguration() = ClasspathDeploymentConfiguration()
}
