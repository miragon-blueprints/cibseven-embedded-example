package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class IssueInsurancePolicyDelegate extends BaseDelegate {

    private final IssueInsurancePolicyUseCase useCase;

    public IssueInsurancePolicyDelegate(IssueInsurancePolicyUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        useCase.issuePolicy(ApplicationId.of(execution.getProcessBusinessKey()));
    }
}
