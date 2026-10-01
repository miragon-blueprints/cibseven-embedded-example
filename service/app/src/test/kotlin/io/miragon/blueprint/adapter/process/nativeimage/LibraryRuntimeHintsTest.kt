package io.miragon.blueprint.adapter.process.nativeimage

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates.proxies
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

class LibraryRuntimeHintsTest {

    @Test
    fun `MyBatis can pick its log adapter and language drivers and finds its DTDs`() {

        // given / when: the hints contributed for MyBatis
        val hints = hintsOf(MyBatisRuntimeHints())

        // then: the types MyBatis instantiates by name are constructible
        listOf(
            "org.apache.ibatis.session.Configuration",
            "org.apache.ibatis.logging.slf4j.Slf4jImpl",
            "org.apache.ibatis.scripting.xmltags.XMLLanguageDriver",
            "org.apache.ibatis.scripting.defaults.RawLanguageDriver",
        ).forEach {
            assertThat(reflection().onType(TypeReference.of(it)).withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
                .`as`(it).accepts(hints)
        }
        // and: the Javassist probe resolves and the bundled DTDs are embedded
        assertThat(reflection().onType(TypeReference.of("org.apache.ibatis.javassist.util.proxy.ProxyFactory"))).accepts(hints)
        assertThat(resource().forResource("org/apache/ibatis/builder/xml/mybatis-3-config.dtd")).accepts(hints)
        assertThat(resource().forResource("org/apache/ibatis/builder/xml/mybatis-3-mapper.dtd")).accepts(hints)
    }

    @Test
    fun `MyBatis can call strings and collections from OGNL and proxy JDBC for statement logging`() {

        // given / when: the hints contributed for MyBatis
        val hints = hintsOf(MyBatisRuntimeHints())

        // then: the methods the mapper XML calls on JDK types are invocable
        assertThat(reflection().onMethodInvocation(String::class.java, "equals")).accepts(hints)
        assertThat(reflection().onMethodInvocation(String::class.java, "contains")).accepts(hints)
        assertThat(reflection().onMethodInvocation(java.util.Collection::class.java, "isEmpty")).accepts(hints)
        assertThat(reflection().onMethodInvocation(java.util.AbstractCollection::class.java, "isEmpty")).accepts(hints)
        // and: the JDBC logging proxies can be created
        listOf(
            Connection::class.java,
            Statement::class.java,
            PreparedStatement::class.java,
            CallableStatement::class.java,
            ResultSet::class.java,
        ).forEach {
            assertThat(proxies().forInterfaces(it)).`as`(it.name).accepts(hints)
        }
    }

    @Test
    fun `Jersey providers and factories are injectable by HK2`() {

        // given / when: the hints contributed for Jersey
        val hints = hintsOf(JerseyRuntimeHints())

        // then: Jersey's own providers, the HK2 bridge and the Jackson types Jersey wires are open for injection
        listOf(
            "org.glassfish.jersey.jackson.internal.DefaultJacksonJaxbJsonProvider",
            "org.glassfish.jersey.server.validation.internal.ValidationBinder",
            "org.glassfish.jersey.server.spring.SpringComponentProvider",
            "org.glassfish.jersey.internal.inject.ParamConverters\$AggregatedProvider",
            "org.jvnet.hk2.spring.bridge.internal.SpringIntoHK2BridgeImpl",
            "com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider",
            "com.fasterxml.jackson.jakarta.rs.base.ProviderBase",
            "com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationIntrospector",
        ).forEach {
            assertThat(
                reflection().onType(TypeReference.of(it)).withMemberCategories(
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS,
                ),
            ).`as`(it).accepts(hints)
        }
    }

    @Test
    fun `Jersey finds its service files and message bundles`() {

        // given / when: the hints contributed for Jersey
        val hints = hintsOf(JerseyRuntimeHints())

        // then: the files Jersey's own service finder reads are embedded
        listOf(
            "META-INF/services/org.glassfish.jersey.internal.spi.AutoDiscoverable",
            "META-INF/services/org.glassfish.hk2.extension.ServiceLocatorGenerator",
            "META-INF/services/jakarta.ws.rs.ext.RuntimeDelegate",
            "org/glassfish/jersey/server/spring/localization.properties",
        ).forEach {
            assertThat(resource().forResource(it)).`as`(it).accepts(hints)
        }
    }

    @Test
    fun `the Envers revision entity and repository base class are reflectively usable`() {

        // given / when: the hints contributed for Envers
        val hints = hintsOf(EnversRuntimeHints())

        // then: the types Hibernate maps and Spring Data instantiates are open
        listOf(
            "org.hibernate.envers.DefaultRevisionEntity",
            "org.hibernate.envers.RevisionMapping",
            "org.springframework.data.envers.repository.support.EnversRevisionRepositoryImpl",
        ).forEach {
            assertThat(
                reflection().onType(TypeReference.of(it)).withMemberCategories(
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS,
                ),
            ).`as`(it).accepts(hints)
        }
    }

    @Test
    fun `the process models of this service are embedded`() {

        // given / when: the hints contributed for the process models
        val hints = hintsOf(ProcessModelRuntimeHints())

        // then: every model the auto-deployment scans for is part of the image
        listOf(
            "bpmn/bike-leasing.bpmn",
            "bpmn/cancel-bike-order.bpmn",
            "dmn/check-credit-rating.dmn",
            "forms/clarify-alternative.form",
            "forms/clarify-return.form",
        ).forEach {
            assertThat(resource().forResource(it)).`as`(it).accepts(hints)
        }
    }

    private fun hintsOf(registrar: RuntimeHintsRegistrar) =
        RuntimeHints().also { registrar.registerHints(it, javaClass.classLoader) }
}
