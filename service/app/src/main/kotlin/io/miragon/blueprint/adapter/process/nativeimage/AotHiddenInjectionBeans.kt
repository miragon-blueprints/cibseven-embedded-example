package io.miragon.blueprint.adapter.process.nativeimage

import org.cibseven.bpm.spring.boot.starter.configuration.impl.DefaultAuthorizationConfiguration
import org.cibseven.bpm.spring.boot.starter.configuration.impl.DefaultDatasourceConfiguration
import org.cibseven.bpm.spring.boot.starter.configuration.impl.DefaultFailedJobConfiguration
import org.cibseven.bpm.spring.boot.starter.configuration.impl.DefaultHistoryConfiguration
import org.cibseven.bpm.spring.boot.starter.configuration.impl.DefaultJobConfiguration
import org.cibseven.bpm.spring.boot.starter.configuration.impl.DefaultMetricsConfiguration
import org.cibseven.bpm.spring.boot.starter.configuration.impl.DefaultProcessEngineConfiguration
import org.cibseven.webapp.auth.SevenUserProvider
import org.cibseven.webapp.providers.SevenProvider

/**
 * The CIB seven beans whose `@Bean` method declares a less specific type than the instance it
 * returns, hiding annotated members from Spring AOT. `AotHiddenInjectionBeansTest` derives this list
 * from the running context, so a CIB seven upgrade that adds another one fails the JVM build.
 */
object AotHiddenInjectionBeans {

    val types: Set<Class<*>> =
        setOf(
            DefaultProcessEngineConfiguration::class.java,
            DefaultDatasourceConfiguration::class.java,
            DefaultJobConfiguration::class.java,
            DefaultHistoryConfiguration::class.java,
            DefaultMetricsConfiguration::class.java,
            DefaultAuthorizationConfiguration::class.java,
            DefaultFailedJobConfiguration::class.java,
            SevenProvider::class.java,
            SevenUserProvider::class.java,
        )
}
