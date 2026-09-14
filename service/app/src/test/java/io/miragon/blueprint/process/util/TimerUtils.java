package io.miragon.blueprint.process.util;

import io.miragon.bpmn.runtime.ElementId;
import org.cibseven.bpm.engine.ProcessEngine;
import org.cibseven.bpm.engine.runtime.Job;

public final class TimerUtils {

    private TimerUtils() {
    }

    /**
     * Fires the timer job of the given boundary/catch event directly, regardless of its due date.
     * Replaces clock manipulation: the tests verify that the timer path is wired correctly, not the
     * real-world waiting duration.
     */
    public static void fireTimer(ProcessEngine engine, ElementId timerActivityId) {
        Job timer = engine.getManagementService()
                .createJobQuery()
                .timers()
                .activityId(timerActivityId.getValue())
                .singleResult();
        if (timer == null) {
            throw new IllegalArgumentException(
                    "no timer job found for activity '" + timerActivityId.getValue() + "'");
        }
        engine.getManagementService().executeJob(timer.getId());
    }
}
