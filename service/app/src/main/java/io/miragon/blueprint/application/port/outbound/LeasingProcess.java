package io.miragon.blueprint.application.port.outbound;

import io.miragon.blueprint.domain.bike.BikeId;
import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;

/**
 * Outbound port that drives the BPMN engine: it starts a process instance, correlates the messages
 * that release the process's wait states, and completes the alternative-clarification user task from
 * the outside. Implemented by the CIB seven adapter.
 */
public interface LeasingProcess {

    void submitRequest(LeasingApplication application);

    void correlateContractSigned(ApplicationId id);

    void correlateHandoverReported(ApplicationId id);

    void correlateApplicationWithdrawn(ApplicationId id);

    void completeAlternativeClarification(ApplicationId id, boolean alternativeFound, BikeId bikeId);

    /** Convenience overload for the no-alternative case. */
    default void completeAlternativeClarification(ApplicationId id, boolean alternativeFound) {
        completeAlternativeClarification(id, alternativeFound, null);
    }
}
