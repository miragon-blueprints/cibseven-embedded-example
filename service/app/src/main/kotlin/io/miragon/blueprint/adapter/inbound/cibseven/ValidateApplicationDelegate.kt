package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase
import io.miragon.blueprint.domain.leasing.ApplicationId
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException
import org.cibseven.bpm.engine.delegate.BpmnError
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.springframework.stereotype.Component

@Component
class ValidateApplicationDelegate(
    private val useCase: ValidateApplicationUseCase,
) : BaseDelegate() {

    override fun executeTask(execution: DelegateExecution) {
        try {
            useCase.validate(ApplicationId.of(execution.processBusinessKey))
        } catch (e: ApplicationInvalidException) {
            throw BpmnError("applicationInvalid", e.reason)
        }
    }
}
