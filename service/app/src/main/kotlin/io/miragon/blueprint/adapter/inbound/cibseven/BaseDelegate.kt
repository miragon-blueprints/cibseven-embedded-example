package io.miragon.blueprint.adapter.inbound.cibseven

import io.miragon.blueprint.domain.leasing.ApplicationId
import mu.KotlinLogging
import org.cibseven.bpm.engine.delegate.DelegateExecution
import org.cibseven.bpm.engine.delegate.JavaDelegate

/**
 * Base for all JavaDelegates: wraps the work in a try/catch so failures are logged consistently and
 * re-thrown for the engine to handle. The process business key carries the [ApplicationId], so
 * subclasses read it via [applicationId] instead of reaching for a magic variable name.
 */
abstract class BaseDelegate : JavaDelegate {

    protected val log = KotlinLogging.logger {}

    override fun execute(execution: DelegateExecution) {
        try {
            executeTask(execution)
        } catch (e: Exception) {
            log.error(e) { "Error while processing CIB seven task '${execution.currentActivityId}'" }
            throw e
        }
    }

    protected fun applicationId(execution: DelegateExecution): ApplicationId =
        ApplicationId.of(execution.processBusinessKey)

    abstract fun executeTask(execution: DelegateExecution)
}
