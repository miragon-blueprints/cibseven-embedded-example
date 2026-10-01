package io.miragon.blueprint.adapter.process.nativeimage

import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.RuntimeHintsRegistrar
import org.springframework.aot.hint.TypeReference

/**
 * CIB seven ships no GraalVM reachability metadata, yet the engine is reflective throughout: MyBatis
 * and OGNL read entities and query objects named in the mapper XML, JUEL resolves bean properties of
 * whatever an expression touches, Jackson binds the REST DTOs, loggers, exceptions, commands and
 * plugins are instantiated by class name, and DMN results and object variables travel through Java
 * serialization. Instead of chasing each call site, every CIB seven class outside the reflection-free
 * model API is opened for reflection and serialization, together with the engine's non-class
 * resources (mapper XML, SQL schema scripts, XSDs, plugin descriptors).
 */
class CibSevenRuntimeHints : RuntimeHintsRegistrar {

    override fun registerHints(hints: RuntimeHints, classLoader: ClassLoader?) {
        reflectiveClasses(classLoader).forEach { it.openFor(hints.reflection()) }
        RESOURCE_PATTERNS.forEach { hints.resources().registerPattern(it) }
        hints.resources().registerResourceBundle(CRON_MESSAGES_BUNDLE)
        (JDK_TYPES_INSTANTIATED_BY_NAME + FEEL_SCRIPT_ENGINE_FACTORIES).forEach {
            hints.reflection().registerType(TypeReference.of(it), MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS)
        }
        JDK_TYPES_IN_SERIALIZED_VARIABLES.forEach { hints.reflection().registerJavaSerialization(it) }
    }

    fun reflectiveClasses(classLoader: ClassLoader?): List<ClasspathScan.ScannedClass> =
        ClasspathScan(classLoader)
            .classesIn(ROOT_PACKAGE)
            .filterNot { scanned -> REFLECTION_FREE_PACKAGES.any { scanned.name.startsWith("$it.") } }

    companion object {
        const val ROOT_PACKAGE = "org.cibseven"

        const val CRON_MESSAGES_BUNDLE = "camundajar.impl.com.cronutils.CronUtilsI18N"

        val REFLECTION_FREE_PACKAGES = listOf("org.cibseven.bpm.model")

        val JDK_TYPES_INSTANTIATED_BY_NAME = listOf("java.security.SecureRandom")

        val FEEL_SCRIPT_ENGINE_FACTORIES =
            listOf(
                "org.camunda.feel.impl.script.FeelScriptEngineFactory",
                "org.camunda.feel.impl.script.FeelUnaryTestsScriptEngineFactory",
            )

        val JDK_TYPES_IN_SERIALIZED_VARIABLES: List<Class<*>> =
            listOf(
                String::class.java,
                java.lang.Boolean::class.java,
                java.lang.Number::class.java,
                java.lang.Integer::class.java,
                java.lang.Long::class.java,
                java.lang.Double::class.java,
                java.math.BigDecimal::class.java,
                java.math.BigInteger::class.java,
                java.util.Date::class.java,
                java.util.ArrayList::class.java,
                java.util.HashMap::class.java,
                java.util.LinkedHashMap::class.java,
                java.util.HashSet::class.java,
                java.util.LinkedHashSet::class.java,
            )

        val RESOURCE_PATTERNS =
            listOf(
                "org/cibseven/**/*.xml",
                "org/cibseven/**/*.sql",
                "org/cibseven/**/*.xsd",
                "org/cibseven/**/*.properties",
                "META-INF/services/org.cibseven.*",
                "META-INF/services/javax.script.ScriptEngineFactory",
                "META-INF/services/org.camunda.feel.*",
            )
    }
}
