package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class ActivateLeasingDelegate(
    private val useCase: ActivateLeasingUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        useCase.activate(ApplicationId.of(execution.processBusinessKey))
    }
}
