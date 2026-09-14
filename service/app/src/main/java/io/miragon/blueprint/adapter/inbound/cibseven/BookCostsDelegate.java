package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi.Variables;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.domain.bike.OrderId;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.springframework.stereotype.Component;

@Component
public class BookCostsDelegate extends BaseDelegate {

    private final BookCancellationCostsUseCase useCase;

    public BookCostsDelegate(BookCancellationCostsUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    protected void executeTask(DelegateExecution execution) {
        OrderId orderId = new OrderId(
                (String) execution.getVariable(Variables.StartEventCancellationRequired.ORDER_ID.getValue()));
        useCase.bookCosts(orderId);
    }
}
