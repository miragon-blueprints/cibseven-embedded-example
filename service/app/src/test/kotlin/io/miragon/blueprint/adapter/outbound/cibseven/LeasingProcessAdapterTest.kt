package io.miragon.blueprint.adapter.outbound.cibseven

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Messages
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.testLeasingApplication
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.cibseven.bpm.engine.RuntimeService
import org.cibseven.bpm.engine.TaskService
import org.cibseven.bpm.engine.runtime.MessageCorrelationBuilder
import org.cibseven.bpm.engine.task.Task
import org.cibseven.bpm.engine.task.TaskQuery
import org.junit.jupiter.api.Test
import java.util.UUID

class LeasingProcessAdapterTest {

    private val runtimeService = mockk<RuntimeService>()
    private val taskService = mockk<TaskService>()
    private val underTest = LeasingProcessAdapter(runtimeService = runtimeService, taskService = taskService)

    private val id = ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))

    @Test
    fun `submitRequest starts the process by message with the application variables`() {

        // given: a leasing application and a stubbed runtime service
        val application = testLeasingApplication(id = id)
        every { runtimeService.startProcessInstanceByMessage(any(), any<String>(), any()) } returns mockk()

        // when: the request is submitted
        underTest.submitRequest(application)

        // then: the leasing-request message starts the process, keyed by the application id, with the DMN inputs and bike
        verify {
            runtimeService.startProcessInstanceByMessage(
                Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value,
                id.value.toString(),
                match<Map<String, Any?>> {
                    it["age"] == 35 &&
                        it["monthlyNetIncome"] == 3500.0 &&
                        it["bikeId"] == "BIKE-900"
                },
            )
        }
        confirmVerified(runtimeService)
    }

    @Test
    fun `completeAlternativeClarification completes the user task with the decision and chosen bike`() {

        // given: an open alternative-clarification task for the instance keyed by the application id
        val task = mockk<Task> { every { id } returns "task-1" }
        val query = mockk<TaskQuery>()
        every { taskService.createTaskQuery() } returns query
        every { query.processInstanceBusinessKey(any()) } returns query
        every { query.taskDefinitionKey(any()) } returns query
        every { query.singleResult() } returns task
        every { taskService.complete(any(), any()) } returns Unit

        // when: an alternative bike is selected from the outside
        underTest.completeAlternativeClarification(id, alternativeFound = true, bikeId = BikeId("BIKE-42"))

        // then: the clarify-alternative task is located by business key and completed with the decision variables
        verify { taskService.createTaskQuery() }
        verify { query.processInstanceBusinessKey(id.value.toString()) }
        verify { query.taskDefinitionKey(Elements.USER_TASK_CLARIFY_ALTERNATIVE.value) }
        verify {
            taskService.complete(
                "task-1",
                match<Map<String, Any?>> { it["alternativeFound"] == true && it["bikeId"] == "BIKE-42" },
            )
        }
        confirmVerified(taskService)
    }

    @Test
    fun `correlateContractSigned correlates the message by business key`() {
        assertCorrelation(Messages.MIRAVELO_CONTRACT_SIGNED.value) { underTest.correlateContractSigned(id) }
    }

    @Test
    fun `correlateHandoverReported correlates the message by business key`() {
        assertCorrelation(Messages.MIRAVELO_HANDOVER_REPORTED.value) { underTest.correlateHandoverReported(id) }
    }

    @Test
    fun `correlateApplicationWithdrawn correlates the message by business key`() {
        assertCorrelation(Messages.MIRAVELO_APPLICATION_WITHDRAWN.value) { underTest.correlateApplicationWithdrawn(id) }
    }

    private fun assertCorrelation(expectedMessage: String, action: () -> Unit) {

        // given: a message-correlation builder chain
        val builder = mockk<MessageCorrelationBuilder>()
        every { runtimeService.createMessageCorrelation(any()) } returns builder
        every { builder.processInstanceBusinessKey(any()) } returns builder
        every { builder.correlate() } returns mockk()

        // when: the correlation is triggered
        action()

        // then: the expected message is correlated to the instance carrying the application's business key
        verify { runtimeService.createMessageCorrelation(expectedMessage) }
        verify { builder.processInstanceBusinessKey(id.value.toString()) }
        verify { builder.correlate() }
        confirmVerified(runtimeService, builder)
    }
}
