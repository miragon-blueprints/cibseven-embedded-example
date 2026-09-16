package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class CancelPolicyDelegate extends BaseDelegate {

    private final CancelInsurancePolicyUseCase useCase;

    public CancelPolicyDelegate(CancelInsurancePolicyUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        useCase.cancelPolicy(ApplicationId.of(execution.getProcessBusinessKey()));
    }
}
