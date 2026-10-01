package io.miragon.blueprint.adapter.process.nativeimage

import org.assertj.core.api.Assertions.assertThat
import org.cibseven.bpm.spring.boot.starter.configuration.CamundaMetricsConfiguration
import org.cibseven.bpm.spring.boot.starter.configuration.impl.DefaultMetricsConfiguration
import org.cibseven.bpm.spring.boot.starter.property.CamundaBpmProperties
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.aot.AotDetector
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.core.SpringProperties
import org.springframework.test.util.ReflectionTestUtils

class AotHiddenInjectionBeanPostProcessorTest {

    private val properties = CamundaBpmProperties()
    private val beanFactory =
        DefaultListableBeanFactory().apply {
            registerSingleton("camundaBpmProperties", properties)
            registerBeanDefinition("declaredByInterface", RootBeanDefinition(CamundaMetricsConfiguration::class.java))
            registerBeanDefinition("declaredPrecisely", RootBeanDefinition(DefaultMetricsConfiguration::class.java))
        }
    private val underTest = AotHiddenInjectionBeanPostProcessor().apply { setBeanFactory(beanFactory) }

    @AfterEach
    fun leaveAotMode() {
        SpringProperties.setProperty(AotDetector.AOT_ENABLED, null)
    }

    @Test
    fun `in AOT mode a bean declared by its interface gets its hidden members injected and initialised`() {

        // given: AOT mode and a CIB seven bean whose @Bean method only declares the interface
        SpringProperties.setProperty(AotDetector.AOT_ENABLED, "true")
        val bean = DefaultMetricsConfiguration()

        // when: the bean passes the post-processor
        val result = underTest.postProcessBeforeInitialization(bean, "declaredByInterface")

        // then: the @Autowired field is injected and the @PostConstruct method has derived its state from it
        assertThat(result).isSameAs(bean)
        assertThat(ReflectionTestUtils.getField(bean, "camundaBpmProperties")).isSameAs(properties)
        assertThat(ReflectionTestUtils.getField(bean, "metrics")).isSameAs(properties.metrics)
    }

    @Test
    fun `outside AOT mode the bean is left to Spring's own annotation processing`() {

        // given: a regular JVM run
        val bean = DefaultMetricsConfiguration()

        // when: the bean passes the post-processor
        underTest.postProcessBeforeInitialization(bean, "declaredByInterface")

        // then: nothing is injected here
        assertThat(ReflectionTestUtils.getField(bean, "camundaBpmProperties")).isNull()
    }

    @Test
    fun `a bean whose precise type is known to AOT is not processed twice`() {

        // given: AOT mode and a bean definition that already declares the concrete type
        SpringProperties.setProperty(AotDetector.AOT_ENABLED, "true")
        val bean = DefaultMetricsConfiguration()

        // when: the bean passes the post-processor
        underTest.postProcessBeforeInitialization(bean, "declaredPrecisely")

        // then: injection is left to the AOT-generated code
        assertThat(ReflectionTestUtils.getField(bean, "camundaBpmProperties")).isNull()
    }

    @Test
    fun `beans that are not on the list or have no bean definition are ignored`() {

        // given: AOT mode, an unlisted bean with an @Autowired member and a listed one without a definition
        SpringProperties.setProperty(AotDetector.AOT_ENABLED, "true")
        val unlisted = UnlistedBean()
        val withoutDefinition = DefaultMetricsConfiguration()

        // when: both pass the post-processor
        val result = underTest.postProcessBeforeInitialization(unlisted, "declaredByInterface")
        underTest.postProcessBeforeInitialization(withoutDefinition, "unknownBean")

        // then: neither is touched
        assertThat(result).isSameAs(unlisted)
        assertThat(unlisted.properties).isNull()
        assertThat(ReflectionTestUtils.getField(withoutDefinition, "camundaBpmProperties")).isNull()
    }

    private class UnlistedBean {

        @Autowired
        var properties: CamundaBpmProperties? = null
    }
}
