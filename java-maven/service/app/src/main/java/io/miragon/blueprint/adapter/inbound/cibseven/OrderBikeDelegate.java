package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.Errors;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import org.cibseven.bpm.engine.delegate.BpmnError;
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
        String bikeId = (String) execution.getVariable(FlowNodes.ServiceTaskOrderBike.Variables.BIKE_ID.getValue());
        OrderId orderId;
        try {
            orderId = useCase.orderBike(ApplicationId.of(execution.getProcessBusinessKey()), new BikeId(bikeId));
        } catch (BikeUnavailableException e) {
            throw new BpmnError(Errors.BIKE_UNAVAILABLE.getCode(), e.getMessage());
        }
        execution.setVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue(), orderId.value());
    }
}
