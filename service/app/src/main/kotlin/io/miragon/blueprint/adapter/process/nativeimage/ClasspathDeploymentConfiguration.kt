package io.miragon.blueprint.adapter.process.nativeimage

import org.cibseven.bpm.spring.boot.starter.configuration.impl.DefaultDeploymentConfiguration
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.ResourceArrayPropertyEditor

/**
 * The starter and the engine both call `Resource.getFile()` on the scanned process models — to tell
 * them from directories and to name the deployed resource. Inside a native image the models live in
 * the image's resource file system, which has no `java.io.File` view: the starter swallows the
 * resulting exception and silently deploys nothing. This variant filters through `Resource.isReadable`
 * and the resource URL, hands models from such a file system to the engine as in-memory resources
 * named by their file name — the name a model deployed from the fat jar gets as well — and lets a
 * failing scan stop the start-up.
 */
class ClasspathDeploymentConfiguration : DefaultDeploymentConfiguration() {

    override fun getDeploymentResources(): Set<Resource> {
        val resolver = ResourceArrayPropertyEditor()
        resolver.value = camundaBpmProperties.deploymentResourcePattern
        return resolvedResources(resolver).filter(::isDeployable).map(::withoutFileDependency).toSet()
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolvedResources(resolver: ResourceArrayPropertyEditor) = resolver.value as Array<Resource>

    private fun isDeployable(resource: Resource) = resource.isReadable && !resource.url.toString().endsWith("/")

    private fun withoutFileDependency(resource: Resource): Resource =
        if (resource is FileSystemResource && resource.uri.scheme != DEFAULT_FILE_SYSTEM) {
            InMemoryModel(resource.contentAsByteArray, checkNotNull(resource.filename))
        } else {
            resource
        }

    private class InMemoryModel(content: ByteArray, private val fileName: String) : ByteArrayResource(content) {

        override fun getDescription() = fileName
    }

    private companion object {
        const val DEFAULT_FILE_SYSTEM = "file"
    }
}
