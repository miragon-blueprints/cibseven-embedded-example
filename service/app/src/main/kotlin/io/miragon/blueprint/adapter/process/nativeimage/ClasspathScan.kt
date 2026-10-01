package io.miragon.blueprint.adapter.process.nativeimage

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.ReflectionHints
import org.springframework.aot.hint.TypeReference
import org.springframework.util.ClassUtils
import java.io.File
import java.io.Serializable
import java.net.JarURLConnection
import java.net.URL
import java.util.jar.JarFile

/**
 * Lists the classes of a package at AOT build time, each with the kinds of members that can be opened
 * for reflection. Members whose signatures reference an absent optional dependency (OSGi, JBoss VFS,
 * JUnit 3, …) are left out: they cannot be used at run time either, and naming them in a hint only
 * makes the native-image builder stumble over the missing types.
 *
 * Jars are read entry by entry instead of through a `classpath*:` pattern, because for a
 * multi-release jar such a pattern only sees the handful of classes in its versioned directory.
 */
class ClasspathScan(classLoader: ClassLoader?) {

    private val classLoader = classLoader ?: checkNotNull(ClassUtils.getDefaultClassLoader())

    fun classesIn(basePackage: String): List<ScannedClass> {
        val basePath = basePackage.replace('.', '/') + "/"
        return classLoader
            .getResources(basePath)
            .asSequence()
            .flatMap { root -> classFilesBelow(root, basePath) }
            .filterNot { it.substringAfterLast('/') in DESCRIPTORS }
            .map { it.removeSuffix(CLASS_FILE).replace('/', '.') }
            .distinct()
            .mapNotNull(::scanned)
            .toList()
    }

    private fun classFilesBelow(root: URL, basePath: String): List<String> =
        if (root.protocol == "jar") {
            JarFile(File((root.openConnection() as JarURLConnection).jarFileURL.toURI())).use { jar ->
                jar.entries().asSequence().map { it.name }.filter { it.startsWith(basePath) && it.endsWith(CLASS_FILE) }.toList()
            }
        } else {
            val directory = File(root.toURI())
            directory
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith(CLASS_FILE) }
                .map { basePath + it.relativeTo(directory).invariantSeparatorsPath }
                .toList()
        }

    private fun scanned(className: String): ScannedClass? {
        val type = linked { Class.forName(className, false, classLoader) } ?: return null
        return ScannedClass(
            name = className,
            linkableMembers =
                listOfNotNull(
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS.takeIf { linked { type.declaredConstructors } != null },
                    MemberCategory.INVOKE_DECLARED_METHODS.takeIf { linked { type.declaredMethods } != null },
                    MemberCategory.INVOKE_PUBLIC_METHODS.takeIf { linked { type.methods } != null },
                    MemberCategory.ACCESS_DECLARED_FIELDS.takeIf { linked { type.declaredFields } != null },
                ),
            serializable = Serializable::class.java.isAssignableFrom(type),
        )
    }

    private fun <T> linked(lookup: () -> T): T? =
        try {
            lookup()
        } catch (_: LinkageError) {
            null
        } catch (_: ClassNotFoundException) {
            null
        }

    data class ScannedClass(
        val name: String,
        val linkableMembers: List<MemberCategory>,
        val serializable: Boolean,
    ) {

        fun openFor(reflection: ReflectionHints) {
            reflection.registerType(TypeReference.of(name)) {
                it.withMembers(*linkableMembers.toTypedArray()).withJavaSerialization(serializable)
            }
        }
    }

    private companion object {
        const val CLASS_FILE = ".class"

        val DESCRIPTORS = setOf("package-info.class", "module-info.class")
    }
}
