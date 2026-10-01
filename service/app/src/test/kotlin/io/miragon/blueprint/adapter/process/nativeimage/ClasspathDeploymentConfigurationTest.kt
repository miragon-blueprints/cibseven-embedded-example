package io.miragon.blueprint.adapter.process.nativeimage

import org.assertj.core.api.Assertions.assertThat
import org.cibseven.bpm.spring.boot.starter.property.CamundaBpmProperties
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.FileSystemResource
import org.springframework.test.util.ReflectionTestUtils
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path

class ClasspathDeploymentConfigurationTest {

    private val properties = CamundaBpmProperties()
    private val underTest =
        ClasspathDeploymentConfiguration().also { ReflectionTestUtils.setField(it, "camundaBpmProperties", properties) }

    @Test
    fun `resolves the configured patterns to the process models on the classpath`() {

        // given: the patterns this service deploys with
        properties.deploymentResourcePattern = arrayOf("classpath*:**/*.bpmn", "classpath*:**/*.dmn", "classpath*:**/*.form")

        // when: the deployment resources are resolved
        val resources = underTest.deploymentResources

        // then: exactly the models of this service are found
        assertThat(resources.map { it.filename })
            .containsExactlyInAnyOrder(
                "bike-leasing.bpmn",
                "cancel-bike-order.bpmn",
                "check-credit-rating.dmn",
                "clarify-alternative.form",
                "clarify-return.form",
            )
    }

    @Test
    fun `directories matched by a pattern are not deployed`() {

        // given: a pattern that matches the model folder itself as well as a model
        properties.deploymentResourcePattern = arrayOf("classpath*:bpmn*", "classpath*:bpmn/bike-leasing.bpmn")

        // when: the deployment resources are resolved
        val resources = underTest.deploymentResources

        // then: only the model remains
        assertThat(resources.map { it.filename }).containsExactly("bike-leasing.bpmn")
    }

    @Test
    fun `a model on the default file system is deployed as it is`(@TempDir directory: Path) {

        // given: a model file on disk
        val model = FileSystemResource(Files.writeString(directory.resolve("on-disk.bpmn"), "<definitions/>"))

        // when / then: it is handed to the engine unchanged
        assertThat(underTest.withoutFileDependency(model)).isSameAs(model)
    }

    @Test
    fun `a model on a file system without a File view is deployed from memory under its file name`(@TempDir directory: Path) {

        // given: a model inside an archive file system, like the resource file system of a native image
        val archive = URI.create("jar:" + directory.resolve("models.zip").toUri())
        FileSystems.newFileSystem(archive, mapOf("create" to "true")).use { archiveFileSystem ->
            val model = FileSystemResource(Files.writeString(archiveFileSystem.getPath("/embedded.bpmn"), "<definitions/>"))

            // when: its dependency on java.io.File is removed
            val deployable = underTest.withoutFileDependency(model)

            // then: the engine gets the content and a description it can use as resource name
            assertThat(deployable).isNotSameAs(model)
            assertThat(deployable.description).isEqualTo("embedded.bpmn")
            assertThat(deployable.contentAsByteArray).isEqualTo("<definitions/>".toByteArray())
        }
    }
}
