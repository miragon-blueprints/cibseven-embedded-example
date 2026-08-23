package io.miragon.blueprint.application.port.outbound;

import io.miragon.blueprint.domain.leasing.ApplicationId;
import io.miragon.blueprint.domain.leasing.LeasingApplication;
import io.miragon.blueprint.domain.leasing.LeasingStatus;
import java.util.List;
import java.util.Optional;

public interface LeasingApplicationRepository {

    LeasingApplication save(LeasingApplication application);

    Optional<LeasingApplication> findById(ApplicationId id);

    /**
     * Reads a page of applications, optionally filtered by status. The {@link Criteria} and {@link Page}
     * are the port's own types on purpose — Spring Data's {@code Pageable}/{@code Page} stop at the
     * persistence adapter so the application layer never depends on a persistence technology.
     */
    Page findAll(Criteria criteria);

    /** {@code status} may be {@code null} to list applications of every status. */
    record Criteria(LeasingStatus status, int page, int size) {
    }

    record Page(
            List<LeasingApplication> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
