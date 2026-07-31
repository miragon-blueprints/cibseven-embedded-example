package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Variables
import mu.KotlinLogging
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.cibseven.bpm.engine.delegate.ExecutionListener
import org.springframework.stereotype.Component

/**
 * Example [ExecutionListener] on the `serviceTask_orderBike` service task, wired via
 * `camunda:executionListener event="end"` in the BPMN. It fires *after* the `orderBikeDelegate` has
 * run and can read the result variables the delegate wrote, so it simply audit-logs the outcome.
 *
 * Like the delegates, it is a Spring `@Component` referenced by expression (`#{bikeOrderAuditListener}`).
 * A production listener could call a use case instead of logging — exactly like [OrderBikeDelegate].
 */
@Component
class BikeOrderAuditListener : ExecutionListener {

    private val log = KotlinLogging.logger {}

    override fun notify(execution: DelegateExecution) {
        val orderId = execution.getVariable(Variables.ServiceTaskOrderBike.ORDER_ID.value)
        val bikeAvailable = execution.getVariable(Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.value)
        log.info {
            "Bike order finished for application '${execution.processBusinessKey}': " +
                "orderId=$orderId, bikeAvailable=$bikeAvailable"
        }
    }
}
