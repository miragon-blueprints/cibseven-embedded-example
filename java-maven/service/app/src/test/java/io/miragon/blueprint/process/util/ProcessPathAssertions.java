package io.miragon.blueprint.process.util;

import io.miragon.bpmn.runtime.path.PathWalk;

/**
 * Turns a compile-checked {@link PathWalk} into the element ids the engine assertions expect, so
 * process tests never maintain element-id lists by hand.
 */
public final class ProcessPathAssertions {

    private ProcessPathAssertions() {
    }

    /**
     * The ids of every node of the path in walk order, for {@code hasPassedInOrder}. Only meaningful
     * within one sequential branch — walk parallel branches as separate paths.
     */
    public static String[] inWalkOrder(PathWalk.Trail path) {
        return path.getIds().toArray(String[]::new);
    }

    public static String[] inWalkOrder(PathWalk<?, ?> path) {
        return path.getIds().toArray(String[]::new);
    }

    /**
     * The ids of every node of the path without duplicates, for {@code hasPassed}. Use this where the
     * engine does not guarantee an order — e.g. compensation handlers.
     */
    public static String[] inAnyOrder(PathWalk.Trail path) {
        return path.getDistinctIds().toArray(String[]::new);
    }
}
