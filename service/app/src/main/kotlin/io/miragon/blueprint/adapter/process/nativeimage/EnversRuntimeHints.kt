package io.miragon.blueprint.adapter.process.nativeimage

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference

/**
 * Hibernate Envers and Spring Data Envers arrive transitively with the CIB seven webclient. Their
 * mere presence makes Hibernate map the default revision entity and makes Spring Boot build every JPA
 * repository on the Envers repository base class — neither of which the published reachability
 * metadata covers.
 */
class EnversRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        TYPES_MAPPED_OR_INSTANTIATED_REFLECTIVELY.forEach {
            hints.reflection().registerType(
                TypeReference.of(it),
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.ACCESS_DECLARED_FIELDS,
            )
        }
    }

    companion object {
        val TYPES_MAPPED_OR_INSTANTIATED_REFLECTIVELY =
            listOf(
                "org.hibernate.envers.DefaultRevisionEntity",
                "org.hibernate.envers.RevisionMapping",
                "org.springframework.data.envers.repository.support.EnversRevisionRepositoryImpl",
            )
    }
}
