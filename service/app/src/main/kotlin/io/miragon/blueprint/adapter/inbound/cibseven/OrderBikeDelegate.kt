package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class OrderBikeDelegate(
    private val useCase: OrderBikeUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        val result = useCase.orderBike(ApplicationId.of(execution.processBusinessKey))
        execution.setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.value, result.orderId?.value)
        execution.setVariable(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_AVAILABLE.value, result.bikeAvailable)
    }
}
