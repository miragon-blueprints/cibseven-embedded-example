package io.miragon.blueprint.adapter.inbound.cibseven;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.ExecutionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Example {@link ExecutionListener} on the {@code serviceTask_orderBike} service task, wired via
 * {@code camunda:executionListener event="end"} in the BPMN. It fires <em>after</em> the
 * {@code orderBikeDelegate} has run and can read the result variable the delegate wrote, so it simply
 * audit-logs the outcome. No {@code orderId} means the delegate left the task through the
 * {@code bikeUnavailable} BPMN error.
 *
 * <p>Like the delegates, it is a Spring {@code @Component} referenced by expression
 * ({@code #{bikeOrderAuditListener}}). A production listener could call a use case instead of logging.
 */
@Component
public class BikeOrderAuditListener implements ExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(BikeOrderAuditListener.class);

    @Override
    public void notify(DelegateExecution execution) {
        Object orderId = execution.getVariable(FlowNodes.ServiceTaskOrderBike.Variables.ORDER_ID.getValue());
        log.info("Bike order finished for application '{}': orderId={}",
                execution.getProcessBusinessKey(), orderId);
    }
}
