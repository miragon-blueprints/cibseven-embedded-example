import io.miragon.bpmn.adapter.GenerateBpmnModelsTask
import io.miragon.bpmn.domain.shared.OutputLanguage
import io.miragon.bpmn.domain.shared.ProcessEngine
import org.springframework.boot.gradle.tasks.bundling.BootBuildImage
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

springBoot {
    buildInfo()
}

configurations.all {
    exclude(group = "org.cibseven.webapp", module = "cibseven-webclient-web")
}

dependencies {
    implementation(libs.bundles.defaultService)
    implementation(libs.bundles.database)
    implementation(libs.bundles.cibseven)
    implementation(libs.springdoc)
    implementation(libs.bpmn.to.code.runtime)
    testImplementation(libs.bundles.test)
    testImplementation(libs.bundles.cib7ProcessTest)
    testImplementation(libs.bundles.cib7JGiven)
    testImplementation(libs.bpmn.to.code.testing)
    testImplementation(project(":service:common-architecture-tests"))
}

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

val mutationTargetClasses = (project.findProperty("mutationTargetClasses") as String?)
    ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)

val includeProcessTestsInMutation = project.hasProperty("pitProcessSpike")
val requestedMutationEngine = project.findProperty("pitMutationEngine") as String?
val mutateLinesWithKotlinNullChecks = project.hasProperty("pitMutateLinesWithKotlinNullChecks")

if (requestedMutationEngine == "descartes") {
    dependencies { pitest("eu.stamp-project:descartes:1.3.4") }
}

pitest {
    junit5PluginVersion.set("1.2.2")
    targetClasses.set(mutationTargetClasses ?: listOf("io.miragon.blueprint.*"))
    targetTests.set(listOf("io.miragon.blueprint.*"))
    failWhenNoMutations.set(false)
    excludedClasses.set(
        listOf(
            "io.miragon.blueprint.adapter.process.*",
            "io.miragon.blueprint.CibsevenBikeLeasingApplication*",
            "io.miragon.blueprint.BikeCatalogueSeeder*",
            "io.miragon.blueprint.adapter.inbound.rest.DevCorsConfiguration*",
        ) + if (includeProcessTestsInMutation) emptyList() else listOf("io.miragon.blueprint.adapter.inbound.cibseven.*"),
    )
    excludedTestClasses.set(
        listOf("io.miragon.blueprint.architecture.*") +
            if (includeProcessTestsInMutation) emptyList() else listOf("io.miragon.blueprint.process.*"),
    )
    fullMutationMatrix.set(includeProcessTestsInMutation)
    requestedMutationEngine?.let { mutationEngine.set(it) }
    threads.set(Runtime.getRuntime().availableProcessors())
    timeoutFactor.set(BigDecimal("2.0"))
    avoidCallsTo.set(
        (if (mutateLinesWithKotlinNullChecks) emptyList() else listOf("kotlin.jvm.internal")) +
            listOf("mu", "org.slf4j", "io.github.oshai"),
    )
    mutators.set(if (requestedMutationEngine == "descartes") emptyList() else listOf("DEFAULTS"))
    outputFormats.set(listOf("HTML", "XML"))
    timestampedReports.set(false)
    mutationThreshold.set(80)
}

tasks.register<JavaExec>("generateProcessModelMutants") {
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.miragon.blueprint.process.mutation.ProcessModelMutantGeneratorKt")
    args("$projectDir/src/main/resources", layout.buildDirectory.dir("process-model-mutants").get().asFile.path)
}

tasks.withType<BootJar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// OCI image for the backend, built by Spring's Cloud Native Buildpacks integration — no Dockerfile to
// maintain (layered, non-root by default). See docs/adr/0011 and the "Run it in containers" section of
// CONTRIBUTING.md. Build with `./gradlew :service:app:bootBuildImage`.
tasks.named<BootBuildImage>("bootBuildImage") {
    imageName.set("miravelo/cibseven-embedded-example:${project.version}")
    // Pin the JVM the buildpack installs to the version the code targets.
    environment.set(mapOf("BP_JVM_VERSION" to "21"))
}

java.sourceCompatibility = JavaVersion.VERSION_21
