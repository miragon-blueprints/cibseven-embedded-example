package io.miragon.blueprint.adapter.process.nativeimage

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

/**
 * MyBatis 3.5 — the engine's persistence layer — has no reachability metadata of its own. It picks its
 * log adapter and language drivers by class name, evaluates the mapper XML's OGNL conditions through
 * reflection on JDK strings and collections, validates its XML against DTDs bundled in the jar, and
 * wraps JDBC objects in proxies once statement logging is switched to DEBUG. Its configuration also
 * refuses to start unless the shaded Javassist can be found by name — although the engine keeps lazy
 * loading off, so no class is ever generated at run time.
 */
class MyBatisRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        TYPES_INSTANTIATED_BY_NAME.forEach {
            hints.reflection().registerType(TypeReference.of(it), MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
        }
        TYPES_CALLED_FROM_OGNL.forEach {
            hints.reflection().registerType(it, MemberCategory.INVOKE_PUBLIC_METHODS)
        }
        JDBC_TYPES_PROXIED_FOR_STATEMENT_LOGGING.forEach { hints.proxies().registerJdkProxy(it) }
        hints.resources().registerPattern(BUNDLED_DTDS)
        hints.reflection().registerType(TypeReference.of(JAVASSIST_PROBE))
    }

    private companion object {
        const val BUNDLED_DTDS = "org/apache/ibatis/builder/xml/*.dtd"

        const val JAVASSIST_PROBE = "org.apache.ibatis.javassist.util.proxy.ProxyFactory"

        private val TYPES_INSTANTIATED_BY_NAME =
            listOf(
                "org.apache.ibatis.session.Configuration",
                "org.apache.ibatis.logging.slf4j.Slf4jImpl",
                "org.apache.ibatis.scripting.xmltags.XMLLanguageDriver",
                "org.apache.ibatis.scripting.defaults.RawLanguageDriver",
            )

        private val TYPES_CALLED_FROM_OGNL: List<Class<*>> =
            listOf(
                String::class.java,
                java.util.Collection::class.java,
                java.util.AbstractCollection::class.java,
                java.util.List::class.java,
                java.util.Set::class.java,
                java.util.Map::class.java,
            )

        private val JDBC_TYPES_PROXIED_FOR_STATEMENT_LOGGING: List<Class<*>> =
            listOf(
                Connection::class.java,
                Statement::class.java,
                PreparedStatement::class.java,
                CallableStatement::class.java,
                ResultSet::class.java,
            )
    }
}
