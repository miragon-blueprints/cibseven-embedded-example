package io.miragon.blueprint.adapter.process.nativeimage

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import jakarta.annotation.Resource
import jakarta.inject.Inject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.beans.factory.support.RootBeanDefinition
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.lang.reflect.AnnotatedElement

/**
 * Guards [AotHiddenInjectionBeans] against drift: it walks the running context for beans whose
 * `@Bean` method declares a less specific type than the instance it returns while that instance
 * carries annotation-driven members Spring AOT cannot see. Every such bean must be on the list —
 * otherwise it would start half-wired in the native image.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.web-application-type=servlet"],
)
@ActiveProfiles("test")
class AotHiddenInjectionBeansTest {

    @Autowired
    private lateinit var beanFactory: ConfigurableListableBeanFactory

    @Test
    fun `every bean hiding annotated members from AOT is listed`() {

        // given: the fully started application context
        // when: its singletons are checked for members their declared bean type does not reveal
        val hidingBeans =
            beanFactory.beanDefinitionNames
                .mapNotNull(::instanceWithDeclaredType)
                .filter { (bean, declaredType) -> hidesAnnotatedMembers(bean.javaClass, declaredType) }
                .map { (bean, _) -> bean.javaClass }

        // then: the context contains such beans, and each of them is covered by the post-processor
        assertThat(hidingBeans).isNotEmpty()
        assertThat(AotHiddenInjectionBeans.types).containsAll(hidingBeans)
    }

    private fun instanceWithDeclaredType(beanName: String): Pair<Any, Class<*>>? {
        val definition = beanFactory.getMergedBeanDefinition(beanName) as? RootBeanDefinition ?: return null
        if (!definition.isSingleton || definition.isAbstract) return null
        val declaredType = definition.resolvedFactoryMethod?.returnType ?: return null
        val bean = AopUtils.getTargetClass(beanFactory.getBean(beanName)).let { beanFactory.getBean(beanName) }
        return bean to declaredType
    }

    private fun hidesAnnotatedMembers(actualType: Class<*>, declaredType: Class<*>): Boolean =
        generateSequence(actualType) { it.superclass }
            .takeWhile { it != Any::class.java && !it.isAssignableFrom(declaredType) }
            .flatMap { (it.declaredFields.asSequence() + it.declaredMethods + it.declaredConstructors) }
            .any(::isAnnotationDriven)

    private fun isAnnotationDriven(member: AnnotatedElement) =
        ANNOTATIONS_PROCESSED_BY_SPRING.any { member.isAnnotationPresent(it) }

    private companion object {
        val ANNOTATIONS_PROCESSED_BY_SPRING =
            listOf(
                Autowired::class.java,
                Value::class.java,
                Inject::class.java,
                Resource::class.java,
                PostConstruct::class.java,
                PreDestroy::class.java,
            )
    }
}
