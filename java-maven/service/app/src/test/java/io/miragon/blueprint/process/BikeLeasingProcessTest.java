package io.miragon.blueprint.process;

import static io.miragon.blueprint.process.util.JobExecutionUtils.continueToNextWaitState;
import static io.miragon.blueprint.process.util.JobExecutionUtils.executeJobFor;
import static io.miragon.blueprint.process.util.ProcessInstanceUtils.findProcessInstance;
import static io.miragon.blueprint.process.util.ProcessPathAssertions.inAnyOrder;
import static io.miragon.blueprint.process.util.ProcessPathAssertions.inWalkOrder;
import static io.miragon.blueprint.process.util.TimerUtils.fireTimer;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.assertThat;
import static org.cibseven.bpm.engine.test.assertions.bpmn.BpmnAwareTests.init;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.FlowNodes;
import io.miragon.blueprint.adapter.process.CancelBikeOrderProcessApi;
import io.miragon.blueprint.application.port.inbound.ActivateLeasingUseCase;
import io.miragon.blueprint.application.port.inbound.BookCancellationCostsUseCase;
import io.miragon.blueprint.application.port.inbound.CancelContractUseCase;
import io.miragon.blueprint.application.port.inbound.CancelInsurancePolicyUseCase;
import io.miragon.blueprint.application.port.inbound.IssueInsurancePolicyUseCase;
import io.miragon.blueprint.application.port.inbound.OrderBikeUseCase;
import io.miragon.blueprint.application.port.inbound.RejectApplicationUseCase;
import io.miragon.blueprint.application.port.inbound.RequestOrderCancellationUseCase;
import io.miragon.blueprint.application.port.inbound.SendCancellationConfirmationUseCase;
import io.miragon.blueprint.application.port.inbound.SendContractUseCase;
import io.miragon.blueprint.application.port.inbound.SendSignatureReminderUseCase;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.bike.BikeUnavailableException;
import io.miragon.blueprint.domain.bike.OrderId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.CustomerName;
import io.miragon.blueprint.domain.leasing.Email;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import io.miragon.bpmn.runtime.path.PathWalk;
import java.time.LocalDateTime;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.cibseven.bpm.engine.HistoryService;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.task.Task;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class BikeLeasingProcessTest {

    @Autowired
    private LeasingProcess process;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private ProcessEngine processEngine;

    @MockitoBean
    private RejectApplicationUseCase rejectApplicationUseCase;

    @MockitoBean
    private SendContractUseCase sendContractUseCase;

    @MockitoBean
    private CancelContractUseCase cancelContractUseCase;

    @MockitoBean
    private IssueInsurancePolicyUseCase issueInsurancePolicyUseCase;

    @MockitoBean
    private CancelInsurancePolicyUseCase cancelInsurancePolicyUseCase;

    @MockitoBean
    private SendSignatureReminderUseCase sendSignatureReminderUseCase;

    @MockitoBean
    private SendCancellationConfirmationUseCase sendCancellationConfirmationUseCase;

    @MockitoBean
    private RequestOrderCancellationUseCase requestOrderCancellationUseCase;

    @MockitoBean
    private BookCancellationCostsUseCase bookCancellationCostsUseCase;

    @MockitoBean
    private OrderBikeUseCase orderBikeUseCase;

    @MockitoBean
    private ActivateLeasingUseCase activateLeasingUseCase;

    @BeforeEach
    void setUp() {
        init(processEngine);
        when(orderBikeUseCase.orderBike(any())).thenReturn(new OrderId("ORDER-1"));
    }

    @Test
    void happyPathContractSignedBikeAvailableLeasingBecomesActive() {
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // async continuations up to the contract-signature wait state
        executeJobFor(processEngine, FlowNodes.StartEventLeasingRequestReceived.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskSendContract.INSTANCE);

        process.correlateContractSigned(id);
        executeJobFor(processEngine, FlowNodes.EventContractSigned.INSTANCE); // forks into insurance + bike order
        executeJobFor(processEngine, FlowNodes.ServiceTaskIssueInsurancePolicy.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskOrderBike.INSTANCE); // joins -> handover wait state

        process.correlateHandoverReported(id);
        executeJobFor(processEngine, FlowNodes.EventHandoverReported.INSTANCE); // -> withdrawal-period timer

        fireTimer(processEngine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);

        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(inWalkOrder(
                        pathUntilContractSigned()
                                .then(next -> next.gatewayFork())
                                .then(next -> next.serviceTaskIssueInsurancePolicy())
                                .then(next -> next.gatewayJoin())
                                .then(next -> next.eventHandoverReported())
                                .then(next -> next.eventWithdrawalPeriodElapsed())
                                .end(next -> next.endEventLeasingActive())))
                .hasPassedInOrder(inWalkOrder(
                        PathWalk.from(FlowNodes.GatewayFork.INSTANCE)
                                .then(next -> next.gatewayBikeSourceJoin())
                                .then(next -> next.serviceTaskOrderBike())
                                .then(next -> next.gatewayJoin())))
                .hasNotPassed(
                        FlowNodes.EventBikeUnavailable.ELEMENT_ID,
                        FlowNodes.EndEventApplicationRejected.ELEMENT_ID,
                        FlowNodes.EndEventApplicationCancelled.ELEMENT_ID,
                        FlowNodes.EndEventContractCancelled.ELEMENT_ID);

        verify(sendContractUseCase, times(1)).sendContract(id);
        verify(issueInsurancePolicyUseCase, times(1)).issuePolicy(id);
        verify(activateLeasingUseCase, times(1)).activate(id);
    }

    @Test
    void escalationContractNotSignedInTimeIsEscalatedAndRejected() {
        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // async continuations up to the contract-signature wait state
        executeJobFor(processEngine, FlowNodes.StartEventLeasingRequestReceived.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskSendContract.INSTANCE);

        fireTimer(processEngine, FlowNodes.EventSignatureDeadline.INSTANCE); // deadline -> escalation -> rejection
        executeJobFor(processEngine, FlowNodes.ServiceTaskSendRejection.INSTANCE);

        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(inWalkOrder(
                        pathUntilCreditRatingChecked()
                                .onto(next -> next.subProcessConcludeContract())
                                .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                                        pathUntilSignatureAwaited(start)
                                                .then(next -> next.eventSignatureDeadline())
                                                .end(next -> next.endEventNotSigned()))
                                .interruptedBy(
                                        FlowNodes.SubProcessConcludeContract.INSTANCE,
                                        next -> next.eventContractNotSigned())
                                .then(next -> next.gatewayRejectionJoin())
                                .then(next -> next.serviceTaskSendRejection())
                                .end(next -> next.endEventApplicationRejected())))
                .hasNotPassed(FlowNodes.EndEventLeasingActive.ELEMENT_ID);

        verify(rejectApplicationUseCase, times(1)).reject(id);
    }

    @Test
    void notSolventTheDmnRoutesTheApplicationStraightToRejection() {
        // age below 18 cannot sign a leasing contract, so the DMN returns solvent = false
        ApplicationId id = submit(15, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        executeJobFor(processEngine, FlowNodes.StartEventLeasingRequestReceived.INSTANCE); // -> DMN -> not solvent -> rejection
        executeJobFor(processEngine, FlowNodes.ServiceTaskSendRejection.INSTANCE);

        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(inWalkOrder(
                        pathUntilCreditRatingChecked()
                                .then(next -> next.gatewayRejectionJoin())
                                .then(next -> next.serviceTaskSendRejection())
                                .end(next -> next.endEventApplicationRejected())))
                .hasNotPassed(FlowNodes.SubProcessConcludeContract.ELEMENT_ID);

        verify(rejectApplicationUseCase, times(1)).reject(id);
        verify(sendContractUseCase, never()).sendContract(any());
    }

    @Test
    void abortWithdrawingTheApplicationCompensatesTheCompletedSteps() {
        when(requestOrderCancellationUseCase.requestCancellation(any())).thenReturn(true);

        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // async continuations up to the handover wait state (contract signed, bike ordered, insured)
        executeJobFor(processEngine, FlowNodes.StartEventLeasingRequestReceived.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskSendContract.INSTANCE);
        process.correlateContractSigned(id);
        executeJobFor(processEngine, FlowNodes.EventContractSigned.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskIssueInsurancePolicy.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskOrderBike.INSTANCE);

        // Withdrawing triggers compensation. Its handlers run in an engine-defined order, so drive
        // the continuations generically until the cancelBikeOrder sub-process parks on its user task.
        process.correlateApplicationWithdrawn(id);
        continueToNextWaitState(processEngine);

        Task task = taskService
                .createTaskQuery()
                .taskDefinitionKey(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID)
                .singleResult();
        taskService.complete(task.getId(), Map.of("returnClarified", true));
        continueToNextWaitState(processEngine);

        assertThat(instance)
                .isEnded()
                .hasPassed(inAnyOrder(
                        PathWalk.from(FlowNodes.StartEventApplicationWithdrawn.INSTANCE)
                                .then(next -> next.eventReverseApplication())
                                .throwingCompensation(
                                        FlowNodes.EventCompensateContract.INSTANCE,
                                        next -> next.serviceTaskCancelContract())
                                .throwingCompensation(
                                        FlowNodes.EventCompensateInsurance.INSTANCE,
                                        next -> next.serviceTaskCancelPolicy())
                                .throwingCompensation(
                                        FlowNodes.EventCompensateOrder.INSTANCE,
                                        next -> next.callActivityCancelBikeOrder())
                                .then(next -> next.serviceTaskSendCancellationConfirmation())
                                .end(next -> next.endEventApplicationCancelled())))
                .hasNotPassed(FlowNodes.EndEventLeasingActive.ELEMENT_ID);

        verify(cancelContractUseCase, times(1)).cancelContract(id);
        verify(cancelInsurancePolicyUseCase, times(1)).cancelPolicy(id);
        verify(sendCancellationConfirmationUseCase, times(1)).sendCancellationConfirmation(id);
    }

    @Test
    void bikeUnavailableClarifyingAnAlternativeReOrdersAndLeasingBecomesActive() {
        // the first order finds the requested bike unavailable, the re-order after the alternative succeeds
        when(orderBikeUseCase.orderBike(any()))
                .thenThrow(new BikeUnavailableException(new BikeId("BIKE-TEST")))
                .thenReturn(new OrderId("ORDER-2"));

        ApplicationId id = submit(35, 3500.0);
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        // async continuations up to the contract-signature wait state, then fork into insurance + bike order
        executeJobFor(processEngine, FlowNodes.StartEventLeasingRequestReceived.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskSendContract.INSTANCE);
        process.correlateContractSigned(id);
        executeJobFor(processEngine, FlowNodes.EventContractSigned.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskIssueInsurancePolicy.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskOrderBike.INSTANCE); // unavailable -> parks on clarify-alternative

        // the alternative is clarified from the outside — the "external" completion of the user task
        process.completeAlternativeClarification(id, true, new BikeId("BIKE-ALT"));
        continueToNextWaitState(processEngine); // re-order succeeds -> parallel join -> handover wait state

        process.correlateHandoverReported(id);
        executeJobFor(processEngine, FlowNodes.EventHandoverReported.INSTANCE);
        fireTimer(processEngine, FlowNodes.EventWithdrawalPeriodElapsed.INSTANCE);

        assertThat(instance)
                .isEnded()
                .hasPassedInOrder(inWalkOrder(
                        PathWalk.from(FlowNodes.GatewayFork.INSTANCE)
                                .then(next -> next.gatewayBikeSourceJoin())
                                .then(next -> next.serviceTaskOrderBike())
                                .interruptedBy(
                                        FlowNodes.ServiceTaskOrderBike.INSTANCE,
                                        next -> next.eventBikeUnavailable())
                                .then(next -> next.userTaskClarifyAlternative())
                                .then(next -> next.gatewayAlternativeFound())
                                .then(next -> next.gatewayBikeSourceJoin())
                                .then(next -> next.serviceTaskOrderBike())
                                .then(next -> next.gatewayJoin())
                                .then(next -> next.eventHandoverReported())
                                .then(next -> next.eventWithdrawalPeriodElapsed())
                                .end(next -> next.endEventLeasingActive())))
                .hasNotPassed(
                        FlowNodes.EndEventContractCancelled.ELEMENT_ID,
                        FlowNodes.EndEventApplicationRejected.ELEMENT_ID);

        verify(orderBikeUseCase, times(2)).orderBike(id);
        verify(activateLeasingUseCase, times(1)).activate(id);
    }

    @Test
    void bikeUnavailableDecliningTheAlternativeReversesContractAndPolicyWithoutCancellingAnOrder() {
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        process.completeAlternativeClarification(id, false);
        continueToNextWaitState(processEngine); // -> compensation -> cancellation confirmation -> end cancelled

        assertThat(instance)
                .isEnded()
                .hasPassed(inAnyOrder(
                        PathWalk.from(FlowNodes.GatewayAlternativeFound.INSTANCE)
                                .then(next -> next.eventTriggerReversal())
                                .throwingCompensation(
                                        FlowNodes.EventCompensateContract.INSTANCE,
                                        next -> next.serviceTaskCancelContract())
                                .throwingCompensation(
                                        FlowNodes.EventCompensateInsurance.INSTANCE,
                                        next -> next.serviceTaskCancelPolicy())
                                .then(next -> next.serviceTaskConfirmContractCancellation())
                                .end(next -> next.endEventContractCancelled())))
                .hasNotPassed(
                        FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID,
                        FlowNodes.EndEventLeasingActive.ELEMENT_ID);

        verify(cancelContractUseCase, times(1)).cancelContract(id);
        verify(cancelInsurancePolicyUseCase, times(1)).cancelPolicy(id);
        verify(sendCancellationConfirmationUseCase, times(1)).sendCancellationConfirmation(id);
        verify(requestOrderCancellationUseCase, never()).requestCancellation(any());
    }

    @Test
    void abortWhileClarifyingAnAlternativeCompensatesContractAndPolicyWithoutCancellingAnOrder() {
        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        process.correlateApplicationWithdrawn(id);
        continueToNextWaitState(processEngine); // -> compensation -> cancellation confirmation -> end cancelled

        assertThat(instance)
                .isEnded()
                .hasPassed(inAnyOrder(
                        PathWalk.from(FlowNodes.StartEventApplicationWithdrawn.INSTANCE)
                                .then(next -> next.eventReverseApplication())
                                .throwingCompensation(
                                        FlowNodes.EventCompensateContract.INSTANCE,
                                        next -> next.serviceTaskCancelContract())
                                .throwingCompensation(
                                        FlowNodes.EventCompensateInsurance.INSTANCE,
                                        next -> next.serviceTaskCancelPolicy())
                                .then(next -> next.serviceTaskSendCancellationConfirmation())
                                .end(next -> next.endEventApplicationCancelled())))
                .hasNotPassed(FlowNodes.CallActivityCancelBikeOrder.ELEMENT_ID);

        verify(sendCancellationConfirmationUseCase, times(1)).sendCancellationConfirmation(id);
        verify(requestOrderCancellationUseCase, never()).requestCancellation(any());
    }

    @Test
    void abortAfterAnAcceptedAlternativeCancelsTheOnePlacedOrderExactlyOnce() {
        when(requestOrderCancellationUseCase.requestCancellation(any())).thenReturn(true);

        ApplicationId id = submitUntilBikeUnavailable();
        ProcessInstance instance = findProcessInstance(runtimeService, id);

        process.completeAlternativeClarification(id, true, new BikeId("BIKE-ALT"));
        continueToNextWaitState(processEngine); // re-order succeeds -> parallel join -> handover wait state

        process.correlateApplicationWithdrawn(id);
        continueToNextWaitState(processEngine);
        Task task = taskService
                .createTaskQuery()
                .taskDefinitionKey(CancelBikeOrderProcessApi.FlowNodes.UserTaskClarifyReturn.ELEMENT_ID)
                .singleResult();
        taskService.complete(task.getId(), Map.of("returnClarified", true));
        continueToNextWaitState(processEngine);

        assertThat(instance).isEnded().hasPassed(FlowNodes.EndEventApplicationCancelled.ELEMENT_ID);
        long orderCancellations = historyService
                .createHistoricProcessInstanceQuery()
                .processDefinitionKey(CancelBikeOrderProcessApi.PROCESS_ID.getValue())
                .superProcessInstanceId(instance.getId())
                .count();
        Assertions.assertThat(orderCancellations).isEqualTo(1);
    }

    /** Drives a signed, insured application to the clarify-alternative task: the dealer has no bike. */
    private ApplicationId submitUntilBikeUnavailable() {
        when(orderBikeUseCase.orderBike(any()))
                .thenThrow(new BikeUnavailableException(new BikeId("BIKE-TEST")))
                .thenReturn(new OrderId("ORDER-2"));

        ApplicationId id = submit(35, 3500.0);
        executeJobFor(processEngine, FlowNodes.StartEventLeasingRequestReceived.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskSendContract.INSTANCE);
        process.correlateContractSigned(id);
        executeJobFor(processEngine, FlowNodes.EventContractSigned.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskIssueInsurancePolicy.INSTANCE);
        executeJobFor(processEngine, FlowNodes.ServiceTaskOrderBike.INSTANCE); // unavailable -> parks on clarify-alternative
        return id;
    }

    private PathWalk<FlowNodes.GatewayIsSolvent, FlowNodes.GatewayIsSolvent.Next> pathUntilCreditRatingChecked() {
        return PathWalk.from(FlowNodes.StartEventLeasingRequestReceived.INSTANCE)
                .then(next -> next.businessRuleTaskCheckCreditRating())
                .then(next -> next.gatewayIsSolvent());
    }

    private PathWalk<FlowNodes.GatewayAwaitSignature, FlowNodes.GatewayAwaitSignature.Next> pathUntilSignatureAwaited(
            FlowNodes.SubProcessConcludeContract.Start start) {
        return PathWalk.from(start.startEventCustomerEligible())
                .then(next -> next.serviceTaskSendContract())
                .then(next -> next.gatewayAwaitSignature());
    }

    private PathWalk<FlowNodes.SubProcessConcludeContract, FlowNodes.SubProcessConcludeContract.Next>
            pathUntilContractSigned() {
        return pathUntilCreditRatingChecked()
                .onto(next -> next.subProcessConcludeContract())
                .inside(FlowNodes.SubProcessConcludeContract.INSTANCE, start ->
                        pathUntilSignatureAwaited(start)
                                .then(next -> next.eventContractSigned())
                                .end(next -> next.endEventContractValid()));
    }

    private ApplicationId submit(int age, double income) {
        LeasingApplication application = new LeasingApplication(
                ApplicationId.newId(),
                new CustomerName("Test Customer"),
                new Email("test@example.com"),
                age,
                income,
                new BikeId("BIKE-TEST"),
                LeasingStatus.RECEIVED,
                LocalDateTime.now(),
                null,
                null);
        process.submitRequest(application);
        return application.id();
    }
}
