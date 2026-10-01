package io.miragon.blueprint.adapter.process.nativeimage

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference

/**
 * The engine's `/engine-rest` API and the Cockpit / Tasklist backends are JAX-RS applications on
 * Jersey. HK2 builds and injects every provider, factory and resource reflectively, and Jersey
 * locates its extensions through `META-INF/services` files it reads itself. The metadata Jersey
 * ships covers only part of its core modules, so Jersey and its HK2 bridge are opened as a whole.
 */
class JerseyRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        val scan = ClasspathScan(classLoader)
        PACKAGES_WIRED_BY_HK2.flatMap(scan::classesIn).forEach { it.openFor(hints.reflection()) }
        JACKSON_TYPES_WIRED_BY_JERSEY.forEach {
            hints.reflection().registerType(TypeReference.of(it), *INJECTABLE)
        }
        RESOURCE_PATTERNS.forEach { hints.resources().registerPattern(it) }
    }

    private companion object {
        private val PACKAGES_WIRED_BY_HK2 = listOf("org.glassfish.jersey", "org.jvnet.hk2")

        private val JACKSON_TYPES_WIRED_BY_JERSEY =
            listOf(
                "com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider",
                "com.fasterxml.jackson.jakarta.rs.base.ProviderBase",
                "com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector",
            )

        private val INJECTABLE =
            arrayOf(
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.ACCESS_DECLARED_FIELDS,
            )

        private val RESOURCE_PATTERNS =
            listOf(
                "org/glassfish/jersey/**/localization*.properties",
                "META-INF/services/org.glassfish.jersey.*",
                "META-INF/services/org.glassfish.hk2.*",
                "META-INF/services/jakarta.ws.rs.*",
            )
    }
}
