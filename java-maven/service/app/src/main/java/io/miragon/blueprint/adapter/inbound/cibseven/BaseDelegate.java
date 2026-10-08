package io.miragon.blueprint.adapter.inbound.cibseven;

import org.cibseven.bpm.engine.delegate.DelegateExecution;
import org.cibseven.bpm.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for all JavaDelegates: wraps the work in a try/catch so failures are logged consistently and
 * re-thrown for the engine to handle.
 */
public abstract class BaseDelegate implements JavaDelegate {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public void execute(DelegateExecution execution) throws Exception {
        try {
            executeTask(execution);
        } catch (Exception e) {
            log.error("Error while processing CIB seven task '{}'", execution.getCurrentActivityId(), e);
            throw e;
        }
    }

    protected abstract void executeTask(DelegateExecution execution) throws Exception;
}
