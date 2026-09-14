package io.miragon.blueprint.adapter.outbound.cibseven;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Messages;
import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Variables;
import io.miragon.blueprint.application.port.outbound.LeasingProcess;
import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import java.util.HashMap;
import java.util.Map;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.task.Task;
import org.springframework.stereotype.Component;

/**
 * Drives the embedded CIB seven engine. The application id is used as the process business key, so
 * later messages and user-task lookups correlate to the right instance by business key. The variable
 * names come from the typed process API generated from {@code bike-leasing.bpmn}. The fluent engine
 * calls are hidden behind the private helpers at the bottom of this class.
 */
@Component
public class LeasingProcessAdapter implements LeasingProcess {

    private final RuntimeService runtimeService;
    private final TaskService taskService;

    public LeasingProcessAdapter(RuntimeService runtimeService, TaskService taskService) {
        this.runtimeService = runtimeService;
        this.taskService = taskService;
    }

    @Override
    public void submitRequest(LeasingApplication application) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(Variables.StartEventLeasingRequestReceived.APPLICATION_ID.getValue(),
                application.id().value().toString());
        variables.put(Variables.StartEventLeasingRequestReceived.BIKE_ID.getValue(), application.bikeId().value());
        variables.put(Variables.StartEventLeasingRequestReceived.MONTHLY_NET_INCOME.getValue(),
                application.monthlyNetIncome());
        variables.put(Variables.StartEventLeasingRequestReceived.AGE.getValue(), application.age());
        runtimeService.startProcessInstanceByMessage(
                Messages.MIRAVELO_LEASING_REQUEST_RECEIVED.getValue(),
                application.id().value().toString(),
                variables);
    }

    @Override
    public void correlateContractSigned(ApplicationId id) {
        correlateByBusinessKey(Messages.MIRAVELO_CONTRACT_SIGNED.getValue(), id.value().toString());
    }

    @Override
    public void correlateHandoverReported(ApplicationId id) {
        correlateByBusinessKey(Messages.MIRAVELO_HANDOVER_REPORTED.getValue(), id.value().toString());
    }

    @Override
    public void correlateApplicationWithdrawn(ApplicationId id) {
        correlateByBusinessKey(Messages.MIRAVELO_APPLICATION_WITHDRAWN.getValue(), id.value().toString());
    }

    /**
     * Completes the {@code Clarify alternative with customer} user task via the engine client — the
     * same task a human could complete through its deployed Camunda Form in the Tasklist.
     */
    @Override
    public void completeAlternativeClarification(ApplicationId id, boolean alternativeFound, BikeId bikeId) {
        Map<String, Object> variables = new HashMap<>();
        variables.put(Variables.UserTaskClarifyAlternative.ALTERNATIVE_FOUND.getValue(), alternativeFound);
        // The re-order reads the same start-injected bike variable, so reuse its name.
        if (bikeId != null) {
            variables.put(Variables.StartEventLeasingRequestReceived.BIKE_ID.getValue(), bikeId.value());
        }
        completeTask(id.value().toString(), Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue(), variables);
    }

    /** Correlates {@code messageName} to the single running instance carrying {@code businessKey}. */
    private void correlateByBusinessKey(String messageName, String businessKey) {
        runtimeService.createMessageCorrelation(messageName)
                .processInstanceBusinessKey(businessKey)
                .correlate();
    }

    /**
     * Completes the single open {@code taskDefinitionKey} task of the instance carrying
     * {@code businessKey}, passing {@code variables}. Fails loudly when no such task is waiting.
     */
    private void completeTask(String businessKey, String taskDefinitionKey, Map<String, Object> variables) {
        Task task = taskService.createTaskQuery()
                .processInstanceBusinessKey(businessKey)
                .taskDefinitionKey(taskDefinitionKey)
                .singleResult();
        if (task == null) {
            throw new IllegalStateException(
                    "No open '" + taskDefinitionKey + "' task for business key " + businessKey);
        }
        taskService.complete(task.getId(), variables);
    }
}
