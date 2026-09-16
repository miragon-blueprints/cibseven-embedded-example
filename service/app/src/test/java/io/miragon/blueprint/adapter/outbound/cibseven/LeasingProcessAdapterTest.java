package io.miragon.blueprint.adapter.outbound.cibseven;

import static io.miragon.blueprint.domain.leasing.TestObjectBuilder.testLeasingApplication;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Messages;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.UUID;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.runtime.MessageCorrelationBuilder;
import org.cibseven.bpm.engine.task.Task;
import org.cibseven.bpm.engine.task.TaskQuery;
import org.junit.jupiter.api.Test;

class LeasingProcessAdapterTest {

    private final RuntimeService runtimeService = mock(RuntimeService.class);
    private final TaskService taskService = mock(TaskService.class);
    private final LeasingProcessAdapter underTest = new LeasingProcessAdapter(runtimeService, taskService);

    private final ApplicationId id = new ApplicationId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"));

    @Test
    void submitRequestStartsTheProcessByMessageWithTheApplicationVariables() {

        // given: a leasing application and a stubbed runtime service
        LeasingApplication application = testLeasingApplication().id(id).build();

        // when: the request is submitted
        underTest.submitRequest(application);

        // then: the leasing-request message starts the process, keyed by the application id, with the DMN inputs and bike
        verify(runtimeService).startProcessInstanceByMessage(
                eq(Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue()),
                eq(id.value().toString()),
                argThat(vars -> vars.get("age").equals(35)
                        && vars.get("monthlyNetIncome").equals(3500.0)
                        && vars.get("bikeId").equals("BIKE-900")));
        verifyNoMoreInteractions(runtimeService);
    }

    @Test
    void completeAlternativeClarificationCompletesTheUserTaskWithTheDecisionAndChosenBike() {

        // given: an open alternative-clarification task for the instance keyed by the application id
        Task task = mock(Task.class);
        when(task.getId()).thenReturn("task-1");
        TaskQuery query = mock(TaskQuery.class);
        when(taskService.createTaskQuery()).thenReturn(query);
        when(query.processInstanceBusinessKey(any())).thenReturn(query);
        when(query.taskDefinitionKey(any())).thenReturn(query);
        when(query.singleResult()).thenReturn(task);

        // when: an alternative bike is selected from the outside
        underTest.completeAlternativeClarification(id, true, new BikeId("BIKE-42"));

        // then: the clarify-alternative task is located by business key and completed with the decision variables
        verify(taskService).createTaskQuery();
        verify(query).processInstanceBusinessKey(id.value().toString());
        verify(query).taskDefinitionKey(Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue());
        verify(taskService).complete(
                eq("task-1"),
                argThat(vars -> Boolean.TRUE.equals(vars.get("alternativeFound"))
                        && "BIKE-42".equals(vars.get("bikeId"))));
        verifyNoMoreInteractions(taskService);
    }

    @Test
    void correlateContractSignedCorrelatesTheMessageByBusinessKey() {
        assertCorrelation(Messages.MIRAVELO_CONTRACT_SIGNED.getValue(), () -> underTest.correlateContractSigned(id));
    }

    @Test
    void correlateHandoverReportedCorrelatesTheMessageByBusinessKey() {
        assertCorrelation(Messages.MIRAVELO_HANDOVER_REPORTED.getValue(), () -> underTest.correlateHandoverReported(id));
    }

    @Test
    void correlateApplicationWithdrawnCorrelatesTheMessageByBusinessKey() {
        assertCorrelation(
                Messages.MIRAVELO_APPLICATION_WITHDRAWN.getValue(),
                () -> underTest.correlateApplicationWithdrawn(id));
    }

    private void assertCorrelation(String expectedMessage, Runnable action) {

        // given: a message-correlation builder chain
        MessageCorrelationBuilder builder = mock(MessageCorrelationBuilder.class);
        when(runtimeService.createMessageCorrelation(any())).thenReturn(builder);
        when(builder.processInstanceBusinessKey(any())).thenReturn(builder);

        // when: the correlation is triggered
        action.run();

        // then: the expected message is correlated to the instance carrying the application's business key
        verify(runtimeService).createMessageCorrelation(expectedMessage);
        verify(builder).processInstanceBusinessKey(id.value().toString());
        verify(builder).correlate();
        verifyNoMoreInteractions(runtimeService, builder);
    }
}
