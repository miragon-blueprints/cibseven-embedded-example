package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.Variables;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class RequestCancellationDelegate extends BaseDelegate {

    private final RequestOrderCancellationUseCase useCase;

    public RequestCancellationDelegate(RequestOrderCancellationUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        // `orderId` is handed to the cancelBikeOrder sub-process by the calling activity.
        OrderId orderId = new OrderId(
                (String) execution.getVariable(Variables.StartEventCancellationRequired.ORDER_ID.getValue()));
        boolean cancellationPossible = useCase.requestCancellation(orderId);
        execution.setVariable(Variables.ServiceTaskRequestCancellation.CANCELLATION_POSSIBLE.getValue(),
                cancellationPossible);
    }
}
