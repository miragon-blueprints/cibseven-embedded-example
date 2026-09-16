package io.miragon.blueprint.process.util;

import io.miragon.bpmn.runtime.ElementId;
import java.util.List;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.runtime.Job;

/**
 * Helpers for driving async-continuation jobs by hand in process tests (the job executor is disabled
 * in tests).
 */
public final class JobExecutionUtils {

    private JobExecutionUtils() {
    }

    /**
     * Executes the async-continuation job of a <strong>specific</strong> element (by activity id),
     * rather than whichever job happens to be next. This lets a test read as an explicit, ordered
     * trace of the process and fails loudly (no job found) if the expected continuation is missing —
     * which is the safer, more readable choice for the deterministic, linear parts of a flow.
     */
    public static void executeJobFor(ProcessEngine engine, ElementId activityId) {
        Job job = engine.getManagementService()
                .createJobQuery()
                .messages()
                .activityId(activityId.getValue())
                .singleResult();
        if (job == null) {
            throw new IllegalArgumentException(
                    "no async-continuation job found for activity '" + activityId.getValue() + "'");
        }
        engine.getManagementService().executeJob(job.getId());
    }

    /**
     * Drives <em>all</em> pending async-continuation jobs until the process reaches its next wait
     * state. Use this where the exact sequence of continuations is engine-ordered and not worth
     * enumerating — e.g. the compensation chain. For linear, deterministic steps prefer
     * {@link #executeJobFor}.
     */
    public static void continueToNextWaitState(ProcessEngine engine) {
        continueToNextWaitState(engine, 50);
    }

    public static void continueToNextWaitState(ProcessEngine engine, int maxIterations) {
        for (int i = 0; i < maxIterations; i++) {
            List<Job> jobs = engine.getManagementService()
                    .createJobQuery()
                    .active()
                    .messages()
                    .listPage(0, 1);
            if (jobs.isEmpty()) {
                return;
            }
            engine.getManagementService().executeJob(jobs.get(0).getId());
        }
    }
}
