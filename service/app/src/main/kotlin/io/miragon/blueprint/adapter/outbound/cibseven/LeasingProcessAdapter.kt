package io.miragon.blueprint.adapter.outbound.cibseven

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Messages
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Variables
import io.miragon.blueprint.application.port.outbound.LeasingProcess
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.bike.BikeId
import io.miragon.blueprint.domain.leasing.LeasingApplication
import org.cibseven.bpm.engine.RuntimeService
import org.cibseven.bpm.engine.TaskService
import org.springframework.stereotype.Component

/**
 * Drives the embedded CIB seven engine. The application id is used as the process business key, so
 * later messages and user-task lookups correlate to the right instance by business key. The variable
 * names come from the typed process API generated from `bike-leasing.bpmn`. The fluent engine calls
 * are hidden behind the extension helpers in `ProcessEngineExtensions.kt`.
 */
@Component
class LeasingProcessAdapter(
    private val runtimeService: RuntimeService,
    private val taskService: TaskService,
) : LeasingProcess {

    override fun submitRequest(application: LeasingApplication) {
        val start = Variables.StartEventLeasingRequestReceived
        runtimeService.startProcessInstanceByMessage(
            Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.value,
            application.id.value.toString(),
            mapOf(
                start.APPLICATION_ID.value to application.id.value.toString(),
                start.BIKE_ID.value to application.bikeId.value,
                start.MONTHLY_NET_INCOME.value to application.monthlyNetIncome,
                start.AGE.value to application.age,
            ),
        )
    }

    override fun correlateContractSigned(id: ApplicationId) =
        runtimeService.correlateByBusinessKey(Messages.MIRAVELO_CONTRACT_SIGNED.value, id.value.toString())

    override fun correlateHandoverReported(id: ApplicationId) =
        runtimeService.correlateByBusinessKey(Messages.MIRAVELO_HANDOVER_REPORTED.value, id.value.toString())

    override fun correlateApplicationWithdrawn(id: ApplicationId) =
        runtimeService.correlateByBusinessKey(Messages.MIRAVELO_APPLICATION_WITHDRAWN.value, id.value.toString())

    /**
     * Completes the `Clarify alternative with customer` user task via the engine client — the same
     * task a human could complete through its deployed Camunda Form in the Tasklist.
     */
    override fun completeAlternativeClarification(
        id: ApplicationId,
        alternativeFound: Boolean,
        bikeId: BikeId?,
    ) = taskService.completeTask(
        businessKey = id.value.toString(),
        taskDefinitionKey = Elements.USER_TASK_CLARIFY_ALTERNATIVE.value,
        variables = buildMap {
            put(Variables.UserTaskClarifyAlternative.ALTERNATIVE_FOUND.value, alternativeFound)
            // The re-order reads the same start-injected bike variable, so reuse its name.
            bikeId?.let { put(Variables.StartEventLeasingRequestReceived.BIKE_ID.value, it.value) }
        },
    )
}
