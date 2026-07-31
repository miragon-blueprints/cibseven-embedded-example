package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.Variables
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase
import io.miragon.blueprint.domain.bike.OrderId
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class BookCostsDelegate(
    private val useCase: BookCancellationCostsUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        val orderId = OrderId(execution.getVariable(Variables.StartEventCancellationRequired.ORDER_ID.value) as String)
        useCase.bookCosts(orderId)
    }
}
