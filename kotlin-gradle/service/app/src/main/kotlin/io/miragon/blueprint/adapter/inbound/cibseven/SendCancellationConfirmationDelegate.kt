package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class SendCancellationConfirmationDelegate(
    private val useCase: SendCancellationConfirmationUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        useCase.sendCancellationConfirmation(ApplicationId.of(execution.processBusinessKey))
    }
}
