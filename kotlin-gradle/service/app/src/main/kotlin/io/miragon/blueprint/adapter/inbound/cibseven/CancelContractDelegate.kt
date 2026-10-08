package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.CancelContractUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class CancelContractDelegate(
    private val useCase: CancelContractUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        useCase.cancelContract(ApplicationId.of(execution.processBusinessKey))
    }
}
