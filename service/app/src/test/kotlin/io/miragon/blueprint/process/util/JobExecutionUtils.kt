package io.miragon.blueprint.process.util

import io.miragon.bpmn.runtime.ElementId
import org.cibseven.bpm.engine.ProcessEngine

/**
 * Executes the async-continuation job of a **specific** element (by activity id), rather than
 * whichever job happens to be next. This lets a test read as an explicit, ordered trace of the
 * process and fails loudly (no job found) if the expected continuation is missing — which is the
 * safer, more readable choice for the deterministic, linear parts of a flow.
 *
 * The `camunda:asyncAfter` flags in the model create exactly these jobs after each step, and the job
 * executor is disabled in tests, so the test drives them by hand.
 */
fun ProcessEngine.executeJobFor(activityId: ElementId) {
    val job =
        managementService
            .createJobQuery()
            .messages()
            .activityId(activityId.value)
            .singleResult()
    requireNotNull(job) { "no async-continuation job found for activity '${activityId.value}'" }
    managementService.executeJob(job.id)
}

/**
 * Drives *all* pending async-continuation jobs until the process reaches its next wait state. Use
 * this where the exact sequence of continuations is engine-ordered and not worth enumerating — e.g.
 * the compensation chain, whose handlers run in an implementation-defined order. For linear,
 * deterministic steps prefer [executeJobFor], which is explicit about which continuation it fires.
 */
fun ProcessEngine.continueToNextWaitState(maxIterations: Int = 50) {
    repeat(maxIterations) {
        val job =
            managementService
                .createJobQuery()
                .active()
                .messages()
                .listPage(0, 1)
                .firstOrNull() ?: return
        managementService.executeJob(job.id)
    }
}
