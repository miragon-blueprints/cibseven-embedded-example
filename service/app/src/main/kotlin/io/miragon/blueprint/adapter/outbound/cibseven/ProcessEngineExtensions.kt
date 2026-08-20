package io.miragon.blueprint.adapter.outbound.cibseven

import org.cibseven.bpm.engine.RuntimeService
import org.cibseven.bpm.engine.TaskService
import org.cibseven.bpm.engine.task.Task

/**
 * Small extension helpers over the CIB seven services so the adapters read as intent
 * ("correlate this message" / "complete this task") instead of fluent query boilerplate.
 * Everything here keys on the process business key, which this service sets to the application id.
 */

/** Correlates [messageName] to the single running instance carrying [businessKey]. */
fun RuntimeService.correlateByBusinessKey(messageName: String, businessKey: String) {
    createMessageCorrelation(messageName)
        .processInstanceBusinessKey(businessKey)
        .correlate()
}

/**
 * Completes the single open [taskDefinitionKey] task of the instance carrying [businessKey], passing
 * [variables]. Fails loudly when no such task is waiting.
 */
fun TaskService.completeTask(
    businessKey: String,
    taskDefinitionKey: String,
    variables: Map<String, Any> = emptyMap(),
) {
    val task =
        createTaskQuery()
            .processInstanceBusinessKey(businessKey)
            .taskDefinitionKey(taskDefinitionKey)
            .singleResult() ?: error("No open '$taskDefinitionKey' task for business key $businessKey")
    complete(task.id, variables)
}

/** All currently-active tasks of the given [taskDefinitionKey], across every process instance. */
fun TaskService.findOpenTasks(taskDefinitionKey: String): List<Task> =
    createTaskQuery()
        .taskDefinitionKey(taskDefinitionKey)
        .active()
        .list()

/**
 * Maps the given process-instance ids to their business keys in one query. Returns an empty map for
 * an empty input so callers don't issue a pointless query.
 */
fun RuntimeService.businessKeysById(processInstanceIds: Collection<String>): Map<String, String> {
    if (processInstanceIds.isEmpty()) return emptyMap()
    return createProcessInstanceQuery()
        .processInstanceIds(processInstanceIds.toSet())
        .list()
        .associate { it.id to it.businessKey }
}
