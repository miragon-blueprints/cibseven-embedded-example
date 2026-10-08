package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.application.port.inbound.ValidateApplicationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.ApplicationInvalidException;
import org.cibseven.bpm.engine.delegate.BpmnError;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class ValidateApplicationDelegate extends BaseDelegate {

    private final ValidateApplicationUseCase useCase;

    public ValidateApplicationDelegate(ValidateApplicationUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        try {
            useCase.validate(ApplicationId.of(execution.getProcessBusinessKey()));
        } catch (ApplicationInvalidException e) {
            throw new BpmnError("applicationInvalid", e.getReason());
        }
    }
}
