package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Variables;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class OrderBikeDelegate extends BaseDelegate {

    private final OrderBikeUseCase useCase;

    public OrderBikeDelegate(OrderBikeUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        OrderBikeUseCase.Result result = useCase.orderBike(ApplicationId.of(execution.getProcessBusinessKey()));
        execution.setVariable(Variables.ServiceTaskOrderBike.ORDER_ID.getValue(),
                result.orderId() != null ? result.orderId().value() : null);
        execution.setVariable(Variables.ServiceTaskOrderBike.BIKE_AVAILABLE.getValue(), result.bikeAvailable());
    }
}
