package io.miragon.blueprint.process;

import static io.miragon.blueprint.process.util.JobExecutionUtils.executeJobFor;
import static io.miragon.blueprint.process.util.ProcessInstanceUtils.findProcessInstance;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.application.port.outbound.LeasingApplicationRepository;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.time.LocalDateTime;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the out-of-stock order with the <strong>real</strong> use cases instead of mocks. The embedded
 * engine and the {@code @Transactional} services share one transaction, so the
 * {@code BikeUnavailableException} must not mark it rollback-only — otherwise the
 * {@code bikeUnavailable} BPMN error could never be committed and the order job would fail into an
 * incident. {@link BikeLeasingProcessTest} mocks the use cases and therefore cannot see this.
 */
@SpringBootTest
@ActiveProfiles("test")
class BikeUnavailableTransactionTest {

    @Autowired
    private LeasingProcess process;

    @Autowired
    private LeasingApplicationRepository repository;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private ProcessEngine processEngine;

    @BeforeEach
    void setUp() {
        init(processEngine);
    }

    @Test
    void bikeUnavailableTheBpmnErrorRaisedByTheRealOrderServiceIsCommitted() {
        // BIKE-OOS is on the simulated dealer's out-of-stock list
        LeasingApplication application = LeasingApplication.receive(
                ApplicationId.newId(),
                new CustomerName("Test Customer"),
                new Email("test@example.com"),
                35,
                3500.0,
                new BikeId("BIKE-OOS"),
                LocalDateTime.now());
        repository.save(application);
        process.submitRequest(application);
        ProcessInstance instance = findProcessInstance(runtimeService, application.id());

        executeJobFor(processEngine, FlowNodes.StartEventLeasingRequestReceived.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskSendContract.INSTANCE);
        process.correlateContractSigned(application.id());
        executeJobFor(processEngine, FlowNodes.EventContractSigned.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskIssueInsurancePolicy.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskOrderBike.INSTANCE); // unavailable -> BPMN error -> clarify alternative

        assertThat(instance)
                .isWaitingAt(FlowNodes.UserTaskClarifyAlternative.ELEMENT_ID)
                .hasPassed(FlowNodes.EventBikeUnavailable.ELEMENT_ID);
    }
}
