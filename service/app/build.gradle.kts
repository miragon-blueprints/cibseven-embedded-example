import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.springframework.boot.gradle.tasks.bundling.BootJar
import java.math.BigDecimal

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springframework)
    alias(libs.plugins.spring.dependency)
    alias(libs.plugins.bpmnToCode)
    alias(libs.plugins.pitest)
}

configurations.all {
    // CIB seven 2.2.0 pulls in both the generic `cibseven-webclient-web` and the Spring-Boot-4
    // variant `cibseven-webclient-web-spring-boot-4`. The generic one calls
    // PathMatchConfigurer.setUseSuffixPatternMatch(...), which was removed in Spring 7 / Spring
    // Boot 4, and crashes the app on start-up. Drop it so only the SB4 variant remains.
    exclude(group = "org.cibseven.webapp", module = "cibseven-webclient-web")
}

dependencies {
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(libs.bundles.cibseven)
    implementation(libs.bpmn.to.code.runtime)
    testImplementation(libs.bundles.test)
    testImplementation(libs.bundles.cib7ProcessTest)
    testImplementation(libs.bundles.cib7JGiven)
    testImplementation(libs.bpmn.to.code.testing)
    testImplementation(project(":service:common-architecture-tests"))
}

// Generates the typed `*ProcessApi` objects (element ids, messages, timers, variables, …) from the
// BPMN models, so workers and tests reference process elements as compile-checked constants.
tasks.register<GenerateBpmnModelsTask>("generateBpmnModels") {
    baseDir = projectDir.toString()
    filePattern = "src/main/resources/bpmn/*.bpmn"
    outputFolderPath = "$projectDir/src/main/kotlin"
    packagePath = "io.miragon.blueprint.adapter.process"
    outputLanguage = OutputLanguage.KOTLIN
    processEngine = ProcessEngine.CAMUNDA_7
}

tasks.named("classes") {
    dependsOn("generateBpmnModels")
}

tasks.test {
    useJUnitPlatform()
    forkEvery = 1
}

// PIT mutation testing — see docs/mutation-testing.md.
pitest {
    junit5PluginVersion.set("1.2.2")
    targetClasses.set(listOf("io.miragon.blueprint.*"))
    targetTests.set(listOf("io.miragon.blueprint.*"))
    excludedClasses.set(
        listOf(
            "io.miragon.blueprint.adapter.process.*ProcessApi*",
            "io.miragon.blueprint.adapter.process.HistoryCleanupConfiguration*",
            "io.miragon.blueprint.CibsevenBikeLeasingApplication*",
            "io.miragon.blueprint.adapter.inbound.cibseven.*",
        ),
    )
    excludedTestClasses.set(
        listOf(
            "io.miragon.blueprint.process.*",
            "io.miragon.blueprint.architecture.*",
        ),
    )
    threads.set(Runtime.getRuntime().availableProcessors())
    timeoutFactor.set(BigDecimal("2.0"))
    avoidCallsTo.set(listOf("kotlin.jvm.internal", "mu", "org.slf4j", "io.github.oshai"))
    mutators.set(listOf("DEFAULTS"))
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    mutationThreshold.set(80)
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

java.sourceCompatibility = JavaVersion.VERSION_21
