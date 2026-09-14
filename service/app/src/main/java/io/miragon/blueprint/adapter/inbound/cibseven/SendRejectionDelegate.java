package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class SendRejectionDelegate extends BaseDelegate {

    private final RejectApplicationUseCase useCase;

    public SendRejectionDelegate(RejectApplicationUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        useCase.reject(ApplicationId.of(execution.getProcessBusinessKey()));
    }
}
