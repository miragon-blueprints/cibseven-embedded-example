package io.miragon.blueprint.adapter.inbound.cibseven

import mu.KotlinLogging
import org.cibseven.bpm.engine.delegate.DelegateTask
import org.cibseven.bpm.engine.delegate.TaskListener
import org.springframework.stereotype.Component

/**
 * Example [TaskListener] on the `userTask_clarifyAlternative` user task, wired via
 * `camunda:taskListener event="create"` in the BPMN. It fires when the out-of-stock branch parks the
 * human task, and simply audit-logs that manual clarification is required — the human-task counterpart
 * to an [ExecutionListener].
 *
 * Like the delegates, it is a Spring `@Component` referenced by expression
 * (`#{clarifyAlternativeTaskListener}`). A production listener could notify the customer via a
 * `NotificationPort` or route through a use case instead of logging.
 */
@Component
class ClarifyAlternativeTaskListener : TaskListener {

    private val log = KotlinLogging.logger {}

    override fun notify(delegateTask: DelegateTask) {
        log.info {
            "Manual clarification required for application '${delegateTask.execution.processBusinessKey}': " +
                "task '${delegateTask.name}' created"
        }
    }
}
