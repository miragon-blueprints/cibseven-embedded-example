package io.miragon.blueprint.adapter.outbound.cibseven;

import io.miragon.blueprint.adapter.process.BikeLeasingProcessProcessApi.Elements;
import io.miragon.blueprint.application.port.outbound.TaskInboxPort;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.cibseven.bpm.engine.RuntimeService;
import org.cibseven.bpm.engine.TaskService;
import org.cibseven.bpm.engine.runtime.ProcessInstance;
import org.cibseven.bpm.engine.task.Task;
import org.springframework.stereotype.Component;

/**
 * Reads the open {@code Clarify alternative with customer} tasks straight from the engine's task list
 * and translates them into the domain's business key (the application id). It never leaks an engine
 * task id upward: the inbox lists cases, and cases are resolved through the domain, correlated by id.
 */
@Component
public class TaskInboxAdapter implements TaskInboxPort {

    private final TaskService taskService;
    private final RuntimeService runtimeService;

    public TaskInboxAdapter(TaskService taskService, RuntimeService runtimeService) {
        this.taskService = taskService;
        this.runtimeService = runtimeService;
    }

    @Override
    public List<OpenClarification> findOpenClarifications() {
        List<Task> tasks = findOpenTasks(Elements.USER_TASK_CLARIFY_ALTERNATIVE.getValue());
        Map<String, String> businessKeys =
                businessKeysById(tasks.stream().map(Task::getProcessInstanceId).toList());
        List<OpenClarification> result = new ArrayList<>();
        for (Task task : tasks) {
            String applicationId = businessKeys.get(task.getProcessInstanceId());
            if (applicationId == null) {
                continue;
            }
            result.add(new OpenClarification(
                    ApplicationId.of(applicationId),
                    task.getCreateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()));
        }
        return result;
    }

    /** All currently-active tasks of the given {@code taskDefinitionKey}, across every process instance. */
    private List<Task> findOpenTasks(String taskDefinitionKey) {
        return taskService.createTaskQuery()
                .taskDefinitionKey(taskDefinitionKey)
                .active()
                .list();
    }

    /**
     * Maps the given process-instance ids to their business keys in one query. Returns an empty map for
     * an empty input so callers don't issue a pointless query.
     */
    private Map<String, String> businessKeysById(Collection<String> processInstanceIds) {
        if (processInstanceIds.isEmpty()) {
            return Map.of();
        }
        return runtimeService.createProcessInstanceQuery()
                .processInstanceIds(new HashSet<>(processInstanceIds))
                .list()
                .stream()
                .collect(Collectors.toMap(ProcessInstance::getId, ProcessInstance::getBusinessKey));
    }
}
