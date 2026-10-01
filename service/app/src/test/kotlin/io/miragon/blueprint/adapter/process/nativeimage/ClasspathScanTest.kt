package io.miragon.blueprint.adapter.process.nativeimage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection

class ClasspathScanTest {

    private val underTest = ClasspathScan(javaClass.classLoader)

    @Test
    fun `lists the classes of a package including nested ones, without package descriptors`() {

        // given: a package of this service
        // when: it is scanned
        val names = underTest.classesIn("io.miragon.blueprint.domain").map { it.name }

        // then: top-level and nested classes are found, package descriptors are not
        assertThat(names)
            .contains(
                "io.miragon.blueprint.domain.leasing.LeasingApplication",
                "io.miragon.blueprint.domain.leasing.LeasingApplication\$Companion",
            )
            .noneMatch { it.endsWith("package-info") }
    }

    @Test
    fun `a fully linkable class offers all member kinds and reports whether it is serializable`() {

        // given: a package with a serializable enum and a plain data class
        // when: it is scanned
        val scanned = underTest.classesIn("io.miragon.blueprint.domain.leasing").associateBy { it.name }

        // then: every member kind is linkable and the serializable flag follows the type
        val status = scanned.getValue("io.miragon.blueprint.domain.leasing.LeasingStatus")
        val application = scanned.getValue("io.miragon.blueprint.domain.leasing.LeasingApplication")
        assertThat(status.linkableMembers).containsExactlyInAnyOrder(*ALL_MEMBER_KINDS.toTypedArray())
        assertThat(status.serializable).isTrue()
        assertThat(application.serializable).isFalse()
    }

    @Test
    fun `members that reference an absent optional dependency are left out`() {

        // given: the Jersey package holding its OSGi integration, whose constructors and fields use OSGi types
        // when: it is scanned
        val scanned = underTest.classesIn("org.glassfish.jersey.internal").associateBy { it.name }

        // then: only the member kinds that link without OSGi are offered for the integration class
        assertThat(scanned.getValue("org.glassfish.jersey.internal.OsgiRegistry\$OsgiServiceFinder").linkableMembers)
            .doesNotContain(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.ACCESS_DECLARED_FIELDS)
        // and: a class of the same package without such references keeps all member kinds
        assertThat(scanned.getValue("org.glassfish.jersey.internal.Errors").linkableMembers)
            .containsExactlyInAnyOrder(*ALL_MEMBER_KINDS.toTypedArray())
    }

    @Test
    fun `a multi-release jar is scanned beyond its versioned directory`() {

        // given: jersey-common, which ships a handful of Java 21 specific classes next to its base classes
        // when: its package is scanned
        val names = underTest.classesIn("org.glassfish.jersey.internal.inject").map { it.name }

        // then: the base classes are found, each of them once
        assertThat(names).contains("org.glassfish.jersey.internal.inject.ParamConverters\$AggregatedProvider")
        assertThat(names).doesNotHaveDuplicates()
    }

    @Test
    fun `opening a scanned class registers its members and its serializability`() {

        // given: a scanned serializable class
        val status = underTest.classesIn("io.miragon.blueprint.domain.leasing").single { it.name.endsWith("LeasingStatus") }
        val hints = RuntimeHints()

        // when: it is opened for reflection
        status.openFor(hints.reflection())

        // then: the hint carries the member kinds and the serialization flag
        val type = Class.forName(status.name)
        assertThat(reflection().onType(type).withMemberCategories(*ALL_MEMBER_KINDS.toTypedArray())).accepts(hints)
        assertThat(reflection().onJavaSerialization(type, true)).accepts(hints)
    }

    private companion object {
        val ALL_MEMBER_KINDS =
            listOf(
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_DECLARED_METHODS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.ACCESS_DECLARED_FIELDS,
            )
    }
}
