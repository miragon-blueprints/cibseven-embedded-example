package io.miragon.blueprint.adapter.process.nativeimage

import org.assertj.core.api.Assertions.assertThat
import org.cibseven.bpm.dmn.engine.impl.DmnDecisionResultImpl
import org.cibseven.bpm.engine.exception.NullValueException
import org.cibseven.bpm.engine.impl.ProcessEngineLogger
import org.cibseven.bpm.engine.impl.TaskQueryImpl
import org.cibseven.bpm.engine.impl.persistence.entity.ExecutionEntity
import org.cibseven.bpm.engine.rest.dto.runtime.ProcessInstanceDto
import org.cibseven.bpm.model.bpmn.impl.BpmnModelInstanceImpl
import org.junit.jupiter.api.Test
import org.springframework.aot.hint.MemberCategory
import org.springframework.aot.hint.RuntimeHints
import org.springframework.aot.hint.TypeReference
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates.reflection
import org.springframework.aot.hint.predicate.RuntimeHintsPredicates.resource
import java.security.SecureRandom

class CibSevenRuntimeHintsTest {

    private val hints = RuntimeHints().also { CibSevenRuntimeHints().registerHints(it, javaClass.classLoader) }

    @Test
    fun `loggers and exceptions can be instantiated by class name`() {

        // given / when: the hints contributed for the engine
        // then: the types the engine creates through Class.newInstance are constructible
        assertThat(reflection().onType(ProcessEngineLogger::class.java).withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
            .accepts(hints)
        assertThat(reflection().onType(NullValueException::class.java).withMemberCategory(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
            .accepts(hints)
        assertThat(reflection().onType(SecureRandom::class.java).withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
            .accepts(hints)
        // and: so are the FEEL script engines the JDK's script engine manager loads as services
        listOf(
            "org.camunda.feel.impl.script.FeelScriptEngineFactory",
            "org.camunda.feel.impl.script.FeelUnaryTestsScriptEngineFactory",
        ).forEach {
            assertThat(reflection().onType(TypeReference.of(it)).withMemberCategory(MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS))
                .`as`(it).accepts(hints)
        }
    }

    @Test
    fun `entities, queries and REST DTOs are open to MyBatis, JUEL and Jackson`() {

        // given / when: the hints contributed for the engine
        // then: bean-style access works on the persistence, query and REST types
        listOf(ExecutionEntity::class.java, TaskQueryImpl::class.java, ProcessInstanceDto::class.java).forEach {
            assertThat(
                reflection().onType(it).withMemberCategories(
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_METHODS,
                    MemberCategory.ACCESS_DECLARED_FIELDS,
                ),
            ).accepts(hints)
        }
    }

    @Test
    fun `DMN results can travel through Java serialization`() {

        // given / when: the hints contributed for the engine
        // then: the decision result and the JDK types it is built from are serializable
        assertThat(reflection().onJavaSerialization(DmnDecisionResultImpl::class.java, true)).accepts(hints)
        assertThat(reflection().onJavaSerialization(java.util.ArrayList::class.java, true)).accepts(hints)
        assertThat(reflection().onJavaSerialization(String::class.java, true)).accepts(hints)
    }

    @Test
    fun `classes that are not serializable are not registered for serialization`() {

        // given / when: the hints contributed for the engine
        // then: a plain engine class carries no serialization hint
        assertThat(reflection().onJavaSerialization(ProcessEngineLogger::class.java, false)).accepts(hints)
    }

    @Test
    fun `the reflection-free model API stays closed`() {

        // given / when: the hints contributed for the engine
        // then: the BPMN model API, which builds its types through lambdas, is not registered
        assertThat(reflection().onType(BpmnModelInstanceImpl::class.java)).rejects(hints)
    }

    @Test
    fun `mapper XML, schema scripts, schemas and plugin descriptors are embedded`() {

        // given / when: the hints contributed for the engine
        // then: every resource the engine loads by name at start-up or on first use is part of the image
        listOf(
            "org/cibseven/bpm/engine/impl/mapping/mappings.xml",
            "org/cibseven/bpm/engine/impl/mapping/entity/Execution.xml",
            "org/cibseven/bpm/engine/db/create/activiti.postgres.create.engine.sql",
            "org/cibseven/bpm/engine/impl/bpmn/parser/BPMN20.xsd",
            "org/cibseven/bpm/model/bpmn/schema/BPMN20.xsd",
            "org/cibseven/bpm/model/dmn/schema/DMN13.xsd",
            "org/cibseven/bpm/engine/product-info.properties",
            "org/cibseven/bpm/cockpit/plugin/base/queries/processDefinition.xml",
            "META-INF/services/org.cibseven.bpm.cockpit.plugin.spi.CockpitPlugin",
            "META-INF/services/javax.script.ScriptEngineFactory",
            "META-INF/services/org.camunda.feel.valuemapper.CustomValueMapper",
            "plugin/cockpit/app/plugin.js",
            "plugin/tasklist/app/plugin.css",
        ).forEach {
            assertThat(resource().forResource(it)).`as`(it).accepts(hints)
        }
        assertThat(resource().forBundle("camundajar.impl.com.cronutils.CronUtilsI18N")).accepts(hints)
    }

    @Test
    fun `compiled classes are not embedded as resources`() {

        // given / when: the hints contributed for the engine
        // then: class files stay out of the resource section
        assertThat(resource().forResource("org/cibseven/bpm/engine/ProcessEngine.class")).rejects(hints)
    }
}
