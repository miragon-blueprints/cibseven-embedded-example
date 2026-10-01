package io.miragon.blueprint.adapter.process.nativeimage

import jakarta.annotation.PostConstruct
import org.springframework.aot.AotDetector
import org.springframework.beans.factory.BeanFactory
import org.springframework.beans.factory.BeanFactoryAware
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor
import org.springframework.beans.factory.annotation.InitDestroyAnnotationBeanPostProcessor
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.stereotype.Component

/**
 * Spring AOT generates injection code from the bean type it can see at build time — the declared
 * return type of the `@Bean` method. CIB seven declares several beans by interface
 * (`CamundaProcessEngineConfiguration`, `BpmProvider`, …) while the instance carries `@Autowired`,
 * `@Value` and `@PostConstruct` members, so in AOT mode those members are never processed and the
 * engine fails to start. This post-processor replays the annotation-driven injection for exactly
 * those beans. On a regular JVM run Spring's own post-processors do the job and this one stays idle.
 */
@Component
class AotHiddenInjectionBeanPostProcessor : BeanPostProcessor, BeanFactoryAware {

    private lateinit var beanFactory: ConfigurableListableBeanFactory
    private val injection = AutowiredAnnotationBeanPostProcessor()
    private val initialization = InitDestroyAnnotationBeanPostProcessor().apply { setInitAnnotationType(PostConstruct::class.java) }

    override fun setBeanFactory(beanFactory: BeanFactory) {
        this.beanFactory = beanFactory as ConfigurableListableBeanFactory
        injection.setBeanFactory(beanFactory)
    }

    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        if (AotDetector.useGeneratedArtifacts() && hidesInjectionPointsFromAot(bean, beanName)) {
            injection.processInjection(bean)
            initialization.postProcessBeforeInitialization(bean, beanName)
        }
        return bean
    }

    private fun hidesInjectionPointsFromAot(bean: Any, beanName: String): Boolean {
        if (bean.javaClass !in AotHiddenInjectionBeans.types) return false
        if (!beanFactory.containsBeanDefinition(beanName)) return false
        val typeKnownToAot = beanFactory.getMergedBeanDefinition(beanName).resolvableType.resolve()
        return typeKnownToAot != bean.javaClass
    }
}
